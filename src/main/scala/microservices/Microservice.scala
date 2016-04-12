package microservices

import akka.actor.{Actor, ActorLogging}
import microservices.ServiceRegistry.ServiceKey

/**
 * Created by mariojaros
 */
abstract class Microservice(id: String, dependecies: Set[String]) extends Actor with ActorLogging {

  var status: MicroserviceStatus = STOPPED

  override def preStart(): Unit = {
    log.info("MICROSERVICE: Microservice " + id + " started.")
    status = STOPPED
  }

  override def postStop(): Unit = {
    status = STOPPED
  }


  override def receive = microserviceReceive orElse userReceive

  def microserviceReceive: Receive = {
    case RunningMicroservices(microservices) => {
      if (dependecies != null) {
        if (dependecies.forall(dependency => microservices.exists(microservice => microservice.serviceName == dependency))) {
          status = RUNNING
          log.info("Microservice " + self.toString() + " changed her status to RUNNING")
        }
        else {
          status = STOPPED
          log.warning("Microservice " + self.toString() + " changed her status to STOPPED. Be aware some others services could not working")
        }
      }
    }
  }

  def userReceive: Receive

}

sealed trait MicroserviceStatus

case object RUNNING extends MicroserviceStatus

case object STOPPED extends MicroserviceStatus

case class RunningMicroservices(microservices: Set[ServiceKey])
