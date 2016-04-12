package Entity

import Entity.DatabaseMicroService.SaveEmployer
import Entity.StartDatabaseApplicationService.StartDatabaseApplication
import microservices.{Microservice, RUNNING, ServiceRegistryExtension}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
 * Created by mariojaros on 07.04.16.
 */
class StartDatabaseApplicationService(id: String, dependencies: Set[String]) extends Microservice(id, dependencies) {

  override def userReceive: Receive = {

    case StartDatabaseApplication => {
      if (status == RUNNING) {
        log.info("StartDatabaseAppplication: Aplikacia moze bezat")
        val futureDatabaseService = ServiceRegistryExtension.get(context.system).lookup("databaseMicroservices")

        futureDatabaseService onComplete {
          case Success(status) => log.info("DatabaseService: DatabaseApp: prebehlo v poriadku")
          case Failure(status) => log.info("DatabaseService: DatabaseApp: " + status)
        }

        futureDatabaseService foreach (actorRef => {
          println(actorRef.toString())
          actorRef ! SaveEmployer("3", "Peter", "Jaros", "Kvetna", 23)
        })
      }
      else {
        self ! StartDatabaseApplication
      }
    }
  }
}

object StartDatabaseApplicationService {

  case object StartDatabaseApplication

}
