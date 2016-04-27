package microservices

import akka.actor._
import akka.cluster.ClusterEvent.LeaderChanged
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import akka.cluster.{Cluster, ClusterEvent}
import microservices.RegistryInfo.{ServiceKeysInfo, ChangedData, SubscribeService}

import scala.concurrent.duration._
import scala.language.postfixOps

/** Companion object [[ServiceRegistry]] contains case classes that represents messages which [[ServiceRegistry]] can receive. */

object ServiceRegistry {

  val props: Props = Props[ServiceRegistry]

  /** Message for registering microservice.
    *
    * It holds name of service which will represents ID of microservice in the whole distributed system. It also
    * contains reference to microservice where others microservices can send messages.
    *
    * @param name is ID of registering microservice.
    * @param service is reference to microservice.
    */
  case class Register(name: String, service: ActorRef)

  /** Message for looking up microservice by his name (ID).
    *
    * After sending this message [[ServiceRegistry]] will send [[scala.concurrent.Future]].
    * @param name represents ID of searching service.
    */

  case class Lookup(name: String)

  /** Message for terminating and deleting microservice from [[ServiceRegistry]].
    *
    * It serve for deleting reference to service on demand from programmer.
    *
    * @param service to delete from [[ServiceRegistry]].
    */

  case class Terminate(service: ActorRef)

  /** Class represents a key in distributed database.
    *
    * Extends [[Key]] to be able to save in distributed database.
    *
    * @param serviceName is ID which represents microservice in system.
    */
  case class ServiceKey(serviceName: String) extends Key[ORSet[ActorRef]](serviceName)

  /** Message for subscribing a service.
    *
    * When message comes [[ServiceRegistry]] sends [[SubscribeService]] to [[RegistryInfo]]
    *
    * @param actor is reference to microservice.
    */
  case class SubscribeMicroservice(actor: ActorRef)

  /** Message is use to inform subscribed services about actual state of running services.
    *
    */
  case object SentInfoToSubscribers

  /** Key variable providing key for saving all service keys in database. */
  private val AllServicesKey = ORSetKey[ServiceKey]("service-keys")

}

/** Purpose of class is to manage all Microservices.
  *
  * It provides three main functions for management od microservices:
  *
  * [[microservices.ServiceRegistry.Register]]
  *
  * [[microservices.ServiceRegistry.Lookup]]
  *
  * [[microservices.ServiceRegistry.Terminate]]
  *
  * Except for these three functions, it provides some other work for stable running of managment service.
  *
  * Class holds reference of [[Actor]] [[RegistryInfo]] and uses it for informing microservices about their dependencies.
  * When [[ServiceRegistry]] receives [[microservices.ServiceRegistry.SubscribeMicroservice]] message [[SubscribeService]] is sent to
  * reference of [[RegistryInfo]].
  *
  * [[ServiceRegistry]] is exists one per node in the cluster. Data are saved in distributed database. [[Replicator]] serve as
  * API interface to operate with data.
  *
  */
class ServiceRegistry extends Actor with ActorLogging {

  import ServiceRegistry._

  /** Reference to actor which provides interface to operate with data. */
  val replicator = DistributedData(context.system).replicator

  /** Variable holding object of Cluster */
  implicit val cluster = Cluster(context.system)

  /** Set of [[ServiceKey]]. Variable is storing keys of available microservices */
  var keys = Set.empty[ServiceKey]

  /** Map all microservices. Variable contains references of [[Actor]] which are mapped to keys in [[Map]] collection. */
  var services = Map.empty[String, Set[ActorRef]]

  /** Reference to actor [[RegistryInfo]]. Actor provides subscribing services and informs them about available microservices. */
  val serviceRegisterActor = context.system.actorOf(Props[RegistryInfo])

  /** Variable holds information if current node is leader in cluster. */
  var leader = false

  /** Generates [[ServiceKey]] from name of microservices.
    *
    * @param serviceName is ID of microservice.
    * @return object of new [[ServiceKey]].
    */
  def getServiceKey(serviceName: String): ServiceKey =
    ServiceKey(serviceName)

  /** Override [[Actor]] method to implement some logic before start of [[ServiceRegistry]].
    *
    * [[ServiceRegistry]] is subscribed to [[replicator]] to subscribe to events in distributed database.
    * [[ServiceRegistry]] is subscribed to [[cluster]] to subscribe to events in cluster.
    */
  override def preStart(): Unit = {
    replicator ! Subscribe(AllServicesKey, self)
    cluster.subscribe(self, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.LeaderChanged])
  }

  /** Override [[Actor]] method to implement some logic before start of [[ServiceRegistry]]
    *
    * [[ServiceRegistry]] is un subscribed from [[cluster]].
    */
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  /** Override [[Actor]] method receive where all business and behaviour of [[ServiceRegistry]] goes.
    *
    * [[ServiceRegistry]] can receive eight kind of message.
    *
    * When [[Register]] comes with ID and reference to [[Microservice]] it saves into distributed database via [[replicator]] and also
    * it save new [[ServiceKey]] to set of available microservices.
    *
    * When [[Terminated]] comes from [[context]] of dead actor [[ServiceRegistry]] deletes this actor from distributed data.
    *
    * When [[Terminate]] comes with reference to actor microservice it is deleted from distributed data.
    *
    * When [[Changed]] comes it can contains information about changed set of [[ServiceKey]] or changed data of microservices
    * wtih specific key. If there is a new service then [[ServiceRegistry]] subscribes for events of her lifecycle. If set of services
    * with specific key is changed, local variable [[services]] are changing too.
    *
    * When [[SubscribeMicroservice]] or [[SentInfoToSubscribers]] comes [[ServiceRegistry]] sends available services to [[serviceRegisterActor]].
    *
    * When [[LeaderChanged]] comes node, which was as a [[leader]] failed and new one was set.
    */
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
      log.info("SERVICEREGISTRY: I am going end one of this services: " + services)
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
        newServices.foreach( service => context.watch(service))

    case SubscribeMicroservice(actor) => {
      serviceRegisterActor ! SubscribeService(actor)
    }

    case SentInfoToSubscribers => {
      serviceRegisterActor ! ServiceKeysInfo(keys)
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
