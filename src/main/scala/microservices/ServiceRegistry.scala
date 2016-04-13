package microservices

import akka.actor._
import akka.cluster.ClusterEvent.LeaderChanged
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import akka.cluster.{Cluster, ClusterEvent}
import microservices.RegistryInfo.{ChangedData, SubscribeService}

import scala.concurrent.duration._
import scala.language.postfixOps

object ServiceRegistry {

  val props: Props = Props[ServiceRegistry]

  final case class Register(name: String, service: ActorRef)

  final case class Lookup(name: String)

  final case class Terminate(service: ActorRef)

  final case class ServiceKey(serviceName: String) extends Key[ORSet[ActorRef]](serviceName)

  case class SubscribeMicroservice(actor: ActorRef)

  private val AllServicesKey = ORSetKey[ServiceKey]("service-keys")

}

class ServiceRegistry extends Actor with ActorLogging {

  import ServiceRegistry._

  val replicator = DistributedData(context.system).replicator
  implicit val cluster = Cluster(context.system)

  var keys = Set.empty[ServiceKey]
  var services = Map.empty[String, Set[ActorRef]]

  val serviceRegisterActor = context.system.actorOf(Props[RegistryInfo])
  var leader = false

  def getServiceKey(serviceName: String): ServiceKey =
    ServiceKey(serviceName)

  override def preStart(): Unit = {
    replicator ! Subscribe(AllServicesKey, self)
    cluster.subscribe(self, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.LeaderChanged])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Register(id, service) =>
      log.info("SERVICEREGISTRY: New microservices is registering:" + service + " with id: " + id + ".")
      val serviceKey = getServiceKey(id)

      replicator ! Update(serviceKey, ORSet(), WriteAll(timeout = 5 seconds))(_ + service)
      replicator ! Update(AllServicesKey, ORSet(), WriteAll(timeout = 5 seconds))(_ + serviceKey)

    case Terminated(service) =>
      val names = services.collect { case (name, services) if services.contains(service) => name }
      names foreach { name =>
        log.debug("SERVICEREGISTRY: Service with name " + name + " terminated: " + service)
        replicator ! Update(getServiceKey(name), ORSet(), WriteAll(timeout = 5 seconds))(_ - service)
      }

    case Terminate(service) => {
      val names = services.collect { case (name, services) if services.contains(service) => name }
      names.foreach { name =>
        log.warning("SERVICEREGISTRY: Service with name " + name + " terminated: " + service)
        replicator ! Update(getServiceKey(name), ORSet(), WriteAll(timeout = 5 seconds))(_ - service)
      }
    }

    case c@Changed(AllServicesKey) =>
      val newKeys = c.get(AllServicesKey).elements
      log.info("SERVICEREGISTRY: Value of service keys changed. all: " +  newKeys)
      (newKeys -- keys) foreach { key =>
        replicator ! Subscribe(key, self)
      }
      keys = newKeys
      serviceRegisterActor ! ChangedData(keys)

    case c@Changed(ServiceKey(serviceId)) =>
      val newServices = c.get(getServiceKey(serviceId)).elements
      log.info("SERVICEREGISTRY: Services changed for name " + serviceId + ": " + newServices)
      if (newServices.isEmpty) {
        replicator ! Update(AllServicesKey, ORSet(), WriteAll(timeout = 5 seconds))(_ - ServiceKey(serviceId))
      }
      services = services.updated(serviceId, newServices)
      if (leader)
        newServices.foreach(context.watch)

    case SubscribeMicroservice(actor) => {
      serviceRegisterActor ! SubscribeService(actor)
    }
    case LeaderChanged(node) =>
      val wasLeader = leader
      leader = node.exists(_ == cluster.selfAddress)
      if (!wasLeader && leader)
        for (refs ← services.valuesIterator; ref ← refs)
          context.watch(ref)
      else if (wasLeader && !leader)
        for (refs ← services.valuesIterator; ref ← refs)
          context.unwatch(ref)
  }


}
