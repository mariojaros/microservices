package microservices

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, UnreachableMember}
import akka.cluster.ddata.DistributedData
import microservices.RegistryInfo.{ChangedData, SubscribeService}
import microservices.ServiceRegistry.ServiceKey

/**
 * Created by mariojaros
 */
class RegistryInfo extends Actor with ActorLogging {

  var subscribedServices = Set[ActorRef]()

  val cluster = Cluster(context.system)

  val replicator = DistributedData(context.system).replicator

  cluster.state

  override def preStart(): Unit = {

    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])

  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {

    case SubscribeService(id) => {
      log.info("REGISTRYINFO: New subscriber is adding " + id.toString())
      subscribedServices += id
      log.info("REGISTRYINFO: Actual subscribers are: " + subscribedServices)
    }

    case ChangedData(keys) => {
      log.info("REGISTRYINFO: Value of service keys changed. New keys are sending all subcribers")
      subscribedServices foreach (service => service ! RunningMicroservices(keys))
    }

  }
}

object RegistryInfo {

  case class SubscribeService(actor: ActorRef)

  case class ChangedData(keys: Set[ServiceKey])

}


