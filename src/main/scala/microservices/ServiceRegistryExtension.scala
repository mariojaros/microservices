package microservices

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, GSetKey, Key, ORSet}
import akka.event.slf4j.Logger
import akka.pattern.ask
import akka.util.Timeout
import microservices.ServiceRegistry.{Register, SubscribeMicroservice}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mariojaros on 18.10.15.
 */
class ServiceRegistryExtension(system: ExtendedActorSystem) extends Extension {

  val replicator = DistributedData(system).replicator
  implicit val node = Cluster(system)
  val serviceRegistry = system.actorOf(Props[ServiceRegistry])

  val serviceRegisterActor = system.actorOf(Props[RegistryInfo])

  final case class ServiceKey(serviceName: String) extends Key[ORSet[ActorRef]](serviceName)

  val ServicesKey = GSetKey[ServiceKey]("services-keys")

  def register(id: String, service: ActorRef): Unit = {
    implicit val timeout = Timeout(5 seconds)
    serviceRegistry ? Register(id, service)
  }

  def lookup(id: String): Future[ActorRef] = {

    implicit val timeout = Timeout(5 seconds)

    val services: Future[ORSet[ActorRef]] = ask(replicator, Get(ServiceKey(id), ReadAll(timeout = 10 seconds))).mapTo[GetResponse[ORSet[ActorRef]]].map(response => {
      response match {
        case responseSucces: GetSuccess[ORSet[ActorRef]] => {
          Logger.apply("ServiceRegistryExtension").info("SERVICEREGISTRYEXTENSION: Microservice " + id + " was find.")
          responseSucces.get(ServiceKey(id))
        }
        case responseNotFound: NotFound[ORSet[ActorRef]] => {
          Logger.apply("ServiceRegistryExtension").info("SERVICEREGISTRYEXTENSION: Microservice" + id + "was not find. Please register actor")
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

  def terminate(id: String): Unit = {
    implicit val timeout = Timeout(5 seconds)
    serviceRegistry ! ServiceRegistry.Terminated(id)
  }

  def subscribe(actor: ActorRef): Unit = {
    serviceRegistry ! SubscribeMicroservice(actor)
  }
}

object ServiceRegistryExtension
  extends ExtensionId[ServiceRegistryExtension]
  with ExtensionIdProvider {

  override def lookup = ServiceRegistryExtension

  override def createExtension(system: ExtendedActorSystem) = new ServiceRegistryExtension(system)

  override def get(system: ActorSystem): ServiceRegistryExtension = super.get(system)
}
