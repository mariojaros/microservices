package webserver

import Entity.DatabaseMicroService.AllEmployers
import Entity.Employer
import akka.util.Timeout
import webserver.WritingOnScreenMicroservice.{Simple, WriteOnScreen, WriteOnScreenThis}
import microservices.{Microservice, ServiceRegistryExtension}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by mariojaros on 07.04.16.
 */
class WritingOnScreenMicroservice(id: String, dependencies: Set[String]) extends Microservice(id, dependencies) {

  implicit val timeout = Timeout(10 seconds)

  override def userReceive: Receive = {
    case WriteOnScreen => {
      val futureDatabaseMicroservice = ServiceRegistryExtension(context.system).lookup("databaseMicroservices")

      println("WritingOnScreenService: " + self.toString())
      futureDatabaseMicroservice onComplete {
        case Success(status) => println("WritingOnScreenMicroservice: FutureDatabaseServicev poriadku")
        case Failure(status) => println("WritingOnScreenMicroservice: " + status);
      }
      futureDatabaseMicroservice foreach (databaseMicroservice => databaseMicroservice ! AllEmployers)
    }
    case WriteOnScreenThis(employer) => {

      println("WritinOnScreenSercice: Vypisujem employers")

      println("Employer: " + employer.name + " " + employer.surname)
      println("His age and address: " + employer.age + employer.address)

    }

    case Simple => {
      println("WritingOnScreenService: Simple")
    }

  }
}

object WritingOnScreenMicroservice {

  case object Simple

  case object WriteOnScreen

  case class WriteOnScreenThis(employer: Employer)

}
