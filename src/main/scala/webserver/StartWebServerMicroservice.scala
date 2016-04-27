package webserver

import entity.DatabaseMicroService.EmployerMessage
import akka.event.slf4j.Logger
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import microservices.{RUNNING, ServiceRegistryExtension, Microservice}
import webserver.StartWebServerMicroservice.StartServer
import webserver.WebServerMicroservice.GET
import webserver.WritingOnScreenMicroservice.WriteOnScreen
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Failure, Success}
import scala.language.postfixOps

/**
 * Created by mariojaros on 11.04.16.
 */
class StartWebServerMicroservice(id: String, dependenies: Set[String]) extends Microservice(id, dependenies) {

  implicit val timeout = Timeout(10 seconds)
  implicit val actorSystem = context.system

  override def userReceive: Receive = {

    case StartServer => {

      if (status == RUNNING) {

        implicit val materializer = ActorMaterializer()

        val futureWebserverMicroservice = ServiceRegistryExtension(context.system).lookup("webserverMicroservices")

        val futureWritingOnScreenMicroservice = ServiceRegistryExtension(context.system).lookup("writingOnScreenMicroservice")

        val route =
          path("employer") {
            get {

              val futureResponse: Future[Future[EmployerMessage]] = futureWebserverMicroservice map { webserverMicroservice => (webserverMicroservice ? GET("id")).mapTo[EmployerMessage] }

              futureResponse onComplete {
                case Success(status) => Logger.apply("WebServer").info("WebServer: futureResponse uspesne skoncila" + status)
                case Failure(failure) => Logger.apply("WebServer").info("WebServer: futureResponse padla pri" + failure)
              }

              onSuccess(futureResponse) { nextfutureResponse => {
                onSuccess(nextfutureResponse) { answer => complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, answer.employer.name)) }
              }
              }

            }
          } ~
            path("employerToScreen") {
              get {

                futureWritingOnScreenMicroservice foreach { writingOnScreenMicroservice => writingOnScreenMicroservice ! WriteOnScreen }


                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "Order to screen all employers was sent"))
              }
            }

        val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

        println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
        StdIn.readLine() // let it run until user presses return
        bindingFuture
          .flatMap(_.unbind()) // trigger unbinding from the port
          .onComplete(_ ⇒ context.system.terminate()) // and shutdown when done
      }


      else {
        log.debug("Posi")
        self ! StartServer
      }
    }
  }
}

object StartWebServerMicroservice {

  case object StartServer

}


