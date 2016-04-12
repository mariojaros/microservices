package microservices

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
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

  final case class Terminated(id: String)

  final case class ServiceKey(serviceName: String) extends Key[ORSet[ActorRef]](serviceName)

  case class SubscribeMicroservice(actor: ActorRef)

  private val AllServicesKey = GSetKey[ServiceKey]("service-keys")

}

class ServiceRegistry extends Actor with ActorLogging {
  import ServiceRegistry._

  val replicator = DistributedData(context.system).replicator
  implicit val node = Cluster(context.system)

  var keys = Set.empty[ServiceKey]
  var services = Map.empty[String, Set[ActorRef]]

  val serviceRegisterActor = context.system.actorOf(Props[RegistryInfo])
  var leader = false

  def serviceKey(serviceName: String): ServiceKey =
    ServiceKey("service:" + serviceName)

  override def preStart(): Unit = {
    replicator ! Subscribe(AllServicesKey, self)
    node.subscribe(self, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.LeaderChanged])
  }

  override def postStop(): Unit = {
    node.unsubscribe(self)
  }

  def receive = {
    case Register(id, service) ⇒
      log.info("SERVICEREGISTRY: New microservices is registering:" + service + " with id: " + id + ".")
      val serviceKey = ServiceKey(id)

      replicator ! Update (serviceKey, ORSet(), WriteAll(timeout = 5 seconds))(_ + service)
      replicator ! Update (AllServicesKey, GSet(), WriteAll(timeout = 5 seconds))(_ + ServiceKey(id))

    case Terminated(id) =>
      log.info("SERVICEREGISTRY: Microservice with id: " + id + " is deleting")
      replicator ! Delete(serviceKey(id),WriteAll(timeout = 5 seconds))

    case c @ Changed(AllServicesKey) =>
      val newKeys =c.get(AllServicesKey).elements
      log.info("SERVICEREGISTRY: Value of service keys changed. {}, all: {}", (newKeys -- keys), newKeys)
      keys = newKeys
      serviceRegisterActor ! ChangedData(keys)

    case SubscribeMicroservice(actor) => {
      serviceRegisterActor ! SubscribeService(actor)
    }
  }



}
