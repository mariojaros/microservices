package entity


import entity.DatabaseMicroService._
import akka.actor.ActorLogging
import controller.MessageControllerMicroService.CheckEmployer
import webserver.WritingOnScreenMicroservice.WriteOnScreenThis
import microservices.{ServiceRegistryExtension, Microservice}

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by mariojaros on 05.04.16.
 */
class DatabaseMicroService(id: String, dependencies: Set[String]) extends Microservice(id, dependencies) with ActorLogging {

  override def userReceive: Receive = {

    case SomeEmployer => {
      log.info("DatabaseService: All employers")

      val employer = Db.query[Employer].whereEqual("publicId", "1").fetchOne().get
      val e = Employer(employer.publicId, employer.name, employer.surname, employer.address, employer.age)

      val futureControllerMicroservices = ServiceRegistryExtension(context.system).lookup("controllerMicroservices")

      futureControllerMicroservices foreach (controllerMicroservice => controllerMicroservice ! CheckEmployer(e))

    }

    case FindEmployer(publicId) => {

      val employer = Db.query[Employer].whereEqual("publicId", publicId).fetchOne().getOrElse(Employer("default", "default", "default", "default", 0))

      println("DatbaseMicroservice: FindEmployer")

      sender() ! EmployerMessage(employer)
    }

    case EmployerChecked(employer) => {
      val futureWritingOnScreenMicroservice = ServiceRegistryExtension(context.system).lookup("writingOnScreenMicroservice")
      futureWritingOnScreenMicroservice foreach(writingOnScreenMicroservice => writingOnScreenMicroservice ! WriteOnScreenThis(employer))
    }

    case SaveEmployer(publicId, name, surname, address, vek) => {
      val employer = Db.save(Employer(publicId, name, surname, address, vek))
      log.info("po ulozeni do databazy")
      log.info("Sprava: po ulozeni do databazy: " + employer.name)
    }

    case DeleteEmployer(publicId) => {
      Db.delete(publicId)
    }

    case UpdateEmployer(publicId, updatedName, updatedSurname, updatedAddress, updatedAge) => {
      Db.query[Employer].whereEqual("publicId", publicId).fetchOne().map(employer => employer.copy(name = updatedName, surname = updatedSurname, address = updatedAddress, age = updatedAge)).map(updatedEmployer => Db.save(updatedEmployer))
    }

  }

}

/**
 *
 */
object DatabaseMicroService {

  /**
   *
   */
  case object SomeEmployer

  /**
   *
   */
  case class SaveEmployer(publicId: String, name: String, surname: String, address: String, vek: Int)

  /**
   *
   */
  case class FindEmployer(publicId: String)

  /**
   *
   */
  case class DeleteEmployer(publicId: String)

  /**
   *
   */
  case class UpdateEmployer(publicId: String, name: String, surname: String, address: String, vek: Int)

  /**
   *
   */
  case class EmployerMessage(employer: Employer)

  /**
   *
   */
  case class EmployerChecked(employer: Employer)

}



