package microservices

import akka.actor.{Actor, ActorLogging, ActorRef}
import microservices.RegistryInfo.{ChangedData, ServiceKeysInfo, SubscribeService}
import microservices.ServiceRegistry.ServiceKey
import scala.language.postfixOps

/** Class has responsibility for informing all her subscribers about available microservices.
  *
  * It contains set of local ActorRef - Subscribers, extends [[Actor]].
  * Implemented [[Microservice]] can subscribe to [[RegistryInfo]] by sending a message [[SubscribeService]] with reference to itself.
  * It is waiting for messages from [[ServiceRegistry]] [[ChangedData]] and [[ServiceKeysInfo]] that contain information about available microservices.
  * When these two messages comes [[RegistryInfo]] sends a message [[RunningMicroservices]] to all subscribers.
  */
class RegistryInfo extends Actor with ActorLogging {

  /** Set of local subscribed microservices.
    *
    * Holds ActorRefs that are subscribed for available microservices.
    * Data are updated when [[SubscribeService]] message comes.
    */
  var subscribedServices = Set[ActorRef]()

  /** Override method of [[Actor]] receive. Implemented three case of possible coming messages.
    *
    * Contains main business logic of RegisterInfo.
    *
    * When [[SubscribeService]] message comes reference to [[akka.actor]] is added to [[subscribedServices]].
    *
    * When [[ChangedData]] message comes [[RegistryInfo]] informs all subscribed services.
    *
    * When [[ServiceKeysInfo]] message comes [[RegistryInfo]] informs all subcribed services too.
    *
    */
  override def receive: Receive = {

    case SubscribeService(id) => {
      log.info("REGISTRYINFO: New subscriber is adding " + id.toString())
      subscribedServices += id
      ServiceRegistryExtension(context.system).sentKeys()
      log.info("REGISTRYINFO: Actual subscribers are: " + subscribedServices)
    }

    case ChangedData(keys) => {
      log.info("REGISTRYINFO: Value of service keys changed. New keys are sending all subcribers")
      subscribedServices foreach (service => service ! RunningMicroservices(keys))
    }
    case ServiceKeysInfo(keys) => {
      log.info("REGISTRYINFO: ServiceKeysInfo message come. New keys are sending all subcribers")
      subscribedServices foreach (service => service ! RunningMicroservices(keys))
    }

  }
}

/** Companion object of RegistryInfo
  *
  * Contains case classes for receiving messages.
  */
object RegistryInfo {

  /** Message to subscribe microservice. Subscribed microservice will be getting info about running services.
    *
    * @param actor is reference to microservice to subscribe.
    */

  case class SubscribeService(actor: ActorRef)

  /** Message comes when data in distributed database change. It contains [[Set]] of running service keys.
    *
    * @param keys is [[Set]] of [[ServiceKey]] which represents microservices in system.
    */
  case class ChangedData(keys: Set[ServiceKey])

  /** Message has an information about running microservices.
    *
    * Message is use to inform subscribers about available services when it is needed. Not only when keys changed.
    *
    * @param keys is [[Set]] of [[ServiceKey]] which represents microservices in system.
    */
  case class ServiceKeysInfo(keys: Set[ServiceKey])

}


