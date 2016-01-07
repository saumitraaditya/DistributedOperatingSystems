package com.example


import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer

object Boot extends App {
  
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  /*
   *System makes use of a collection of actors to load balance
   * duties, these actors--node's are  responsible for a chunk 
   * of users
   * 
   * */
  val numNodes=1000
  // we create NumberOfNodes node-actors
  var graph = ArrayBuffer[ActorRef]()
   for (i <- 1 to numNodes)
   {
      graph += system.actorOf(Props(new node()),"node"+i.toString)
   }
  // Next we need to configure all nodes in graph so that they are
  // aware of other nodes in the system
  for (Node <- graph)
  {
    Node ! configure(graph)
  }

  // create and start our service actor
  val service = system.actorOf(Props(new MyServiceActor(graph)),"SocialBook")
  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}
