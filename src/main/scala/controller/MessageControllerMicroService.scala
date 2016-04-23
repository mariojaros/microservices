package controller

import entity.DatabaseMicroService.EmployerChecked
import controller.MessageControllerMicroService.CheckEmployer
import entity.Employer
import microservices.Microservice

/**
 * Created by mariojaros on 10.04.16.
 */
class MessageControllerMicroService(id: String, dependencies: Set[String]) extends Microservice(id, dependencies) {

  override def userReceive: Receive = {

    case CheckEmployer(employer) => {
      if (employer.age > 18) sender ! EmployerChecked(employer)
    }
  }
}

object MessageControllerMicroService {

  case class CheckEmployer(employer: Employer)

}
