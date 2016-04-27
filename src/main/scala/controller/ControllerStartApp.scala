package controller

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import microservices.ServiceRegistryExtension
import webserver.WebServerMicroservice
import scala.language.postfixOps

import scala.concurrent.duration._

/**
 * Created by mariojaros on 10.04.16.
 */
object ControllerStartApp {

  def main (args: Array[String]) {
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + "3002").
      withFallback(ConfigFactory.load())

    implicit val actorSystem = ActorSystem("ClusterSystem", config)

    implicit val timeout = Timeout(30 seconds)

    Cluster(actorSystem).registerOnMemberUp {
      DistributedData(actorSystem)

      val webserverMicroservice = actorSystem.actorOf(Props(new WebServerMicroservice("webserverMicroservices", null)))

      val controllerMicroservice = actorSystem.actorOf(Props(new MessageControllerMicroService("controllerMicroservices", null)))

      ServiceRegistryExtension(actorSystem).register("controllerMicroservices", controllerMicroservice)
    }
  }
}
