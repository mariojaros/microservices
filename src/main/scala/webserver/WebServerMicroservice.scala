package webserver

import Entity.DatabaseMicroService.{EmployerMessage, FindEmployer}
import akka.pattern.ask
import akka.util.Timeout
import webserver.WebServerMicroservice.GET
import microservices.{Microservice, ServiceRegistryExtension}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
 * Created by mariojaros on 06.04.16.
 */
class WebServerMicroservice(id: String, dependencies: Set[String]) extends Microservice(id, dependencies) {

  implicit val timeout = Timeout(5 seconds)


  override def userReceive: Receive = {
    case GET(id) => {
      println("Sprava: som v aktorovi")
      val futureDatabaseMicroservices = ServiceRegistryExtension(context.system).lookup("databaseMicroservices")

      futureDatabaseMicroservices onComplete {
        case Success(status) => log.info("WebServerMicroservice: FutureDatabaseMicroservices skoncilo ok" + status)
        case Failure(status) => log.info("WebServerMicroservice: Skoncilo pri futureDatabaseMicroservices" + status)
      }

      futureDatabaseMicroservices foreach (databaseservices => log.info("WebServerMicorservices: Nasiel som aktora" + databaseservices.toString()))

      val futureEmployer = futureDatabaseMicroservices map (databaseMicroservices => (databaseMicroservices ? FindEmployer(id)).mapTo[EmployerMessage])

      futureEmployer onComplete {
        case Success(status) => log.info("WebServerMicroservices: futureEmployer skoncilo oks")
        case Failure(status) => println("WebServiceMicroservices: Skoncilo pri futureEmployer" + status)
      }
      val s = sender
      futureEmployer foreach (futureDatabaseMicroservice => futureDatabaseMicroservice foreach (employerMessage => s ! employerMessage))

    }
  }
}

object WebServerMicroservice {

  case class GET(id: String)


}
