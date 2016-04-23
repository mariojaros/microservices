/** Contains all classes for creating distributed system of microservices.
  *
  * The main class is [[microservices.ServiceRegistry]]. It handles
  * almost all business work around the system including Register, Lookup and Terminate microservices
  * and other underneath stuff. The interface to use funcionality of [[microservices.ServiceRegistry]]
  * for developers is [[microservices.ServiceRegistryExtension]].
  *
  * [[microservices.Microservice]] abstract class is prepare for extend to implement
  * elementary unit which can receive message and handle some work.
  *
  * The last class in this package [[microservices.RegistryInfo]] is responsible for
  * inform microservices about each other status.
  */
package microservices

import akka.actor.{ActorLogging, Actor}
import microservices.ServiceRegistry.ServiceKey

/** The main and basic unit to implement for creating a Microservice.
  *
  * Class extends [[akka.actor.Actor]] and represents functionality of microservice.
  * The main purpose is implement the abstract method userReceive where developer can put
  * and write down business of the microservice. Microservice has to have its own id and
  * it may have set of other microservices dependencies.
  *
  * Developer have access to variable status which can be change to state [[microservices.RUNNING]]
  * when microservice is ready to be available.
  *
  * The basic behaviour is implemented in microserviceReceive partial function. When all dependencies
  * are not running the microservice is going down and to state [[microservices.STOPPED]]
  *
  *
  * @param id ID of microservice. It is basic key, representing microservice in whole system.
  * @param dependecies Set of dependencies which microservice need.
  */
abstract class Microservice(id: String, dependecies: Set[String]) extends Actor with ActorLogging {

  /** State of microservice which can rich all kind of status which implement [[microservices.MicroserviceStatus]]
    *
    * This variable is holding actual state.
    * Microservices can be in two basic states: [[microservices.RUNNING]] and [[microservices.STOPPED]].
    * Status is changing according to microservice's dependencies. Developers can change status in accordance to
    * his own logic.
    * Developers can add their states by extending [[microservices.MicroserviceStatus]]
    *
   */
  var status: MicroserviceStatus = RUNNING

  /** Override method preStart() for log info about starting Microservice. */
  override def preStart(): Unit = {
    log.info("MICROSERVICE: Microservice " + id + " started with status: " + status)
  }

  /** Override method postStop() for change status to STOPPED. */
  override def postStop(): Unit = {
    status = STOPPED
  }

  /** Implemented receive method of class [[Actor]]. Contains all business logic of the microservice.
    *
    * Method contains two partial function: microserviceReceive and not - implemented function userReceive.
    * All two methods are composed to one for implementation of receive method of class Actor.
    * More details about these two methods can be found in their description.
    */
  override def receive = microserviceReceive orElse userReceive

  /** Partial function of receive method. Catches [[microservices.RunningMicroservices]] message.
    *
    * It changes status of microservice according to running microservices. When at least one dependency
    * is not available then status changes to [[microservices.STOPPED]] and service stops her activity by
    * sending the message [[microservices.ServiceRegistry.Terminate]] to [[microservices.ServiceRegistry]] via
    * [[ServiceRegistryExtension]].
    * Running Microservices are included in incoming message [[RunningMicroservices]].
    */
  def microserviceReceive: Receive = {
    case RunningMicroservices(microservices) => {
      changeStatus(microservices)
    }
  }

  /** Main function to implement where all business logic goes.
    *
    * Implementing this method give to the Microservice business logic and behaviour.
    * Syntax is the same as syntax of implementing receive method of class [[Actor]] where
    * pattern matching is use.
    * Developers can implement it to handle various kind of messages and map behaviour to them.
    *
    */
  def userReceive: Receive

  /** Supportive method for microserviceReceive. Takes a set of keys and changes status according to running dependencies.
    *
    * Changes status according to set of microservices that are part of method parameter.
    * @param microservices contains all microservices running.
    */
  def changeStatus(microservices: Set[ServiceKey]) = {
    if (dependecies != null) {
      if (dependecies.forall(dependency => microservices.exists(microservice => microservice.serviceName == dependency))) {
        if (status == STOPPED) {
          status = RUNNING
          ServiceRegistryExtension(context.system).register(id, self)
          log.info("Microservice " + id + " changed her status to RUNNING")
        }
      }
      else {
        if (status == RUNNING) {
          status = STOPPED
          ServiceRegistryExtension(context.system).terminate(self)
          log.warning("Microservice " + id + " changed her status to STOPPED. Be aware some others services could not working")
        }
      }
    }
  }

}

/** Trait for representing status of Microservices.
  *
  * It can be extended by various states. Default are [[RUNNING]] and [[STOPPED]]
  */
sealed trait MicroserviceStatus

/** Represents status of Microservice that is running. */
case object RUNNING extends MicroserviceStatus

/** Represents status of Microservice that is stopped. */
case object STOPPED extends MicroserviceStatus

/** Message with set of available services.
  *
  * When microservice receive that message it check all its dependencies and changes status if it is needed.
  * @param microservices is set of available microservices which are represented as [[ServiceKey]]
  */
case class RunningMicroservices(microservices: Set[ServiceKey])
