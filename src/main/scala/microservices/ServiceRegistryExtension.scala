package microservices

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet}
import akka.event.slf4j.Logger
import akka.pattern.ask
import akka.util.Timeout
import microservices.ServiceRegistry._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/** ServiceRegistry extension provide interface to main functionality of [[ServiceRegistry]].
  *
  * Class is implementing [[Extension]] class through is added new features to Akka system.
  * [[ServiceRegistryExtension]] is loaded once per [[ActorSystem]]. Whole features is comprised
  * of 2 basic components: [[Extension]] and [[ExtensionId]]
  *
  * In implemented class [[ServiceRegistryExtension]] provide API interface functionality of microservices system.
  * There are five methods: [[register()]], [[lookup()]], [[terminate()]], [[subscribe()]] and [[sentKeys()]].
  *
  * Extension is wrapping [[ServiceRegistry]] actor and use his for all his work.
  * @param system is current [[ActorSystem]].
  */
class ServiceRegistryExtension(system: ExtendedActorSystem) extends Extension {

  /** Variable for reference to actor [[akka.cluster.ddata.Replicator]].
    *
    * Actor provides the API for interact with the distributed data. Specially with set of actorRef.
    *
    */
  val replicator = DistributedData(system).replicator

  /** Implicit variable for reference to cluster of system. */
  implicit val node = Cluster(system)

  /** Reference to actor [[ServiceRegistry]]. It provides the API for interact with management of services. */
  val serviceRegistry = system.actorOf(Props[ServiceRegistry])

  /** Registers a microservice in distributed data with a specific ID.
    *
    * Extension use a [[ServiceRegistry]] actor to handle this job.
    *
    * @param id presents the key in key - value store.
    * @param service reference to the concrete actor [[Microservice]].
    */
  def register(id: String, service: ActorRef): Unit = {
    implicit val timeout = Timeout(5 seconds)
    serviceRegistry ! Register(id, service)
  }

  /** Finds a reference to actor [[Microservice]] by ID.
    *
    * Method is return [[Future]] of operation for finding and returning reference to [[Actor]].
    *
    * When key is not exist then empty [[ActorRef]] is returning.
    *
    * @param id is name of service key.
    * @return [[Future]] class of reference to actor.
    */
  def lookup(id: String): Future[ActorRef] = {

    implicit val timeout = Timeout(5 seconds)

    val services: Future[ORSet[ActorRef]] = ask(replicator, Get(ServiceKey(id), ReadAll(timeout = 10 seconds))).mapTo[GetResponse[ORSet[ActorRef]]].map(response => {
      response match {
        case responseSucces: GetSuccess[ORSet[ActorRef]] => {
          Logger.apply("ServiceRegistryExtension").info("SERVICEREGISTRYEXTENSION: Microservice " + id + " was find.")
          responseSucces.get(ServiceKey(id))
        }
        case responseNotFound: NotFound[ORSet[ActorRef]] => {
          Logger.apply("ServiceRegistryExtension").info("SERVICEREGISTRYEXTENSION: Microservice " + id + " was not find. Please register actor")
          ORSet.empty[ActorRef]
        }
        case responseFailure: GetFailure[ORSet[ActorRef]] => {
          Logger.apply("ServiceRegistryExtension").error("SERVICEREGISTRYEXTENSION: " + responseFailure)
          ORSet.empty[ActorRef]
        }
      }
    })
    val futureActorRef = services.map(set => {
      println("Poziadavka na " + id + " ma ulozenych tychto aktorov " + set.getElements())
      set.getElements().toArray().lastOption.getOrElse(ActorRef)
    })

    futureActorRef.mapTo[ActorRef]
  }

  /** Terminate a concrete microservice on demand.
    *
    * @param microservice is reference to Actor to terminate.
    */
  def terminate(microservice: ActorRef): Unit = {
    Logger.apply("ServiceRegistryExtension").warn("SERVICEREGISTRYEXTENSION: I am terminating service: " + microservice)
    serviceRegistry ! Terminate(microservice)
  }

  def subscribe(actor: ActorRef): Unit = {
    serviceRegistry ! SubscribeMicroservice(actor)
  }
  def sentKeys(): Unit = {
    serviceRegistry ! SentInfoToSubscribers
  }
}

object ServiceRegistryExtension
  extends ExtensionId[ServiceRegistryExtension]
  with ExtensionIdProvider {

  override def lookup = ServiceRegistryExtension

  override def createExtension(system: ExtendedActorSystem) = new ServiceRegistryExtension(system)

  override def get(system: ActorSystem): ServiceRegistryExtension = super.get(system)
}
