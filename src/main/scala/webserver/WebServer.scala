package webserver


import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import microservices.ServiceRegistryExtension
import webserver.StartWebServerMicroservice.StartServer
import scala.language.postfixOps

import scala.concurrent.duration._

/**
 * Created by mariojaros on 06.04.16.
 */
object WebServer {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + "3001").
      withFallback(ConfigFactory.load())

    implicit val actorSystem = ActorSystem("ClusterSystem", config)
    // needed for the future flatMap/onComplete in the end
    implicit val timeout = Timeout(30 seconds)

    Cluster(actorSystem).registerOnMemberUp {
      DistributedData(actorSystem)

      val webserverMicroservice = actorSystem.actorOf(Props(new WebServerMicroservice("webserverMicroservices", Set("databaseMicroservices"))))

      val writingOnScreenMicroservice = actorSystem.actorOf(Props(new WritingOnScreenMicroservice("writingOnScreenMicroservice", Set("databaseMicroservices"))))

      ServiceRegistryExtension(actorSystem).register("writingOnScreenMicroservice", writingOnScreenMicroservice)

      ServiceRegistryExtension(actorSystem).register("webserverMicroservices", webserverMicroservice)

      val startWebServerMicroservice = actorSystem.actorOf(Props(new StartWebServerMicroservice("startWebServerMicroservice", Set("writingOnScreenMicroservice", "webserverMicroservices"))))

      ServiceRegistryExtension(actorSystem).subscribe(startWebServerMicroservice)

      ServiceRegistryExtension(actorSystem).subscribe(webserverMicroservice)

      startWebServerMicroservice ! StartServer
    }
  }
}
