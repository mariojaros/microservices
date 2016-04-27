package entity

import entity.StartDatabaseApplicationService.StartDatabaseApplication
import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import com.typesafe.config.ConfigFactory
import microservices.ServiceRegistryExtension

/**
 * Created by mariojaros on 05.04.16.
 */
object DatabaseApplication {

  def main(args: Array[String]) {
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + "3000").
      withFallback(ConfigFactory.load())

    val actorSystem = ActorSystem("ClusterSystem", config)

    Cluster(actorSystem).registerOnMemberUp {
      DistributedData(actorSystem)

      Database.init()

      val databaseService = actorSystem.actorOf(Props(new DatabaseMicroService("databaseMicroservices", Set("controllerMicroservices"))))

      ServiceRegistryExtension.get(actorSystem).register("databaseMicroservices", databaseService)

      ServiceRegistryExtension(actorSystem).subscribe(databaseService)

      val startApplication = actorSystem.actorOf(Props(new StartDatabaseApplicationService("startDatabaseApplication", Set("databaseMicroservices", "controllerMicroservices"))))

      ServiceRegistryExtension.get(actorSystem).subscribe(startApplication)

      startApplication ! StartDatabaseApplication

    }
  }


}
