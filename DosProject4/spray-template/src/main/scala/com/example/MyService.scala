package com.example

import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import spray.routing._
import spray.http._
import MediaTypes._
import scala.collection.mutable.ArrayBuffer
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.{ after, ask, pipe }
import akka.pattern.AskTimeoutException
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._


case class Wall(name: String, items: Array[WallPost])
object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val WallPostFormat = jsonFormat5(WallPost)
  implicit val WallFormat = jsonFormat2(Wall)
}

case class mypost(content:String,id:Int)
case class Person(name:String,items:Array[mypost])
object personProtocol extends DefaultJsonProtocol{
  implicit val postFormat = jsonFormat2(mypost)
  implicit val personFormat = jsonFormat2(Person)
}

object FriendListProtocol extends DefaultJsonProtocol{
  implicit val FriendListFormat = jsonFormat1(returnFriendList)
}

case class myProfile(uid:Int,name:String,sex:String,location:String,age:Int)
object myProfileProtocol extends DefaultJsonProtocol{
  implicit val myProfileFormat = jsonFormat5(myProfile)
}

object myPageProtocol extends DefaultJsonProtocol{
  implicit val pagePost = jsonFormat3(page_post)
  implicit val myPage = jsonFormat5(ret_page)
}
// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor(graph:ArrayBuffer[ActorRef]) extends Actor with HttpService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context
  val numNodes = graph.length
  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(
      {
        path("test"){ requestContext=>
          requestContext.complete("SocialBook - REST API")
        }~
        pathPrefix("graph")
        {
          path("register" / IntNumber / Segment) {(uid,name)=>
            requestContext =>
            graph(uid%numNodes) ! registerUser(uid,name)
            requestContext.complete("User Registered")
            }~
           /*path("updateStatus"/ IntNumber / Segment) {(uid,post)=>
            requestContext =>
              graph(uid%numNodes) ! updateStatus(uid,post)
              requestContext.complete("Post submitted to system")
              println("Status recieved")
          }~*/
          path("updateStatus"){
            parameters("uid".as[Int],"status".as[String]){(uid,status)=>
            graph(uid%numNodes) ! updateStatus(uid,status)
            println(status)
            complete{
                "Post submitted to system"
              }
            }
          }~
          path("sendPost"){
            parameters("sender".as[Int],"receiver".as[Int],"post".as[String]){(sender,receiver,post)=>
              graph(sender%numNodes) ! sendPost(sender,receiver,post)
              println("SendPostRequestReceived")
              complete{
                "submitted post from "+sender.toString()+ " to "+receiver.toString()
              }
            }
          }~
          /*path("sendPost" /IntNumber / IntNumber / Segment){(sender,receiver,post)=>
            requestContext =>
              graph(sender%numNodes) ! sendPost(sender,receiver,post)
              requestContext.complete("submitted post from "+sender.toString()+ " to "+receiver.toString())
              println("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ")
          }~*/
          path("getWall" / IntNumber) {(uid)=>
            requestContext =>
              implicit val timeout = new Timeout(5 seconds)
              val a_sys = context.system
              import a_sys.dispatcher
              (graph(uid%numNodes) ? getWall(uid))
                .recover{
                case ex:AskTimeoutException =>{
                  requestContext.complete("Request Timed out")
                  }
                  
                }
                .onSuccess{
                  case result => {
                    val response = result.asInstanceOf[returnWall]
                    println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
                    //println(response.wall(0))
                    import MyJsonProtocol._
                    import spray.json._
                    import spray.util._
                    val payload = Wall("name",response.wall)
                    val x = payload.toJson.prettyPrint
                    requestContext.complete(x)                  
                    }                  
                } //onSuccess                
              }~
              path("makeFriends" / IntNumber /IntNumber) {(initiator_uid,target_uid)=>
                requestContext =>
                graph(initiator_uid%numNodes) ! forgeFriendship(initiator_uid,target_uid) 
                println("FriendRequest "+ initiator_uid.toString() + " -> "+ target_uid.toString())
                requestContext.complete("Friend request sent")
              }~
              path("friendList" / IntNumber) {(uid)=>
                requestContext =>
                 
                  implicit val timeout = new Timeout(5 seconds)
                  val a_sys = context.system
                  import a_sys.dispatcher
                  (graph(uid%numNodes) ? getFriends(uid))
                    .recover{
                    case ex:AskTimeoutException =>{
                      requestContext.complete("Request Timed out")
                      }
                      
                    }
                      .onSuccess{
                      case result => {
                        val response = result.asInstanceOf[returnFriendList]
                        println("FriendListRequestReceived")
                        println(response.friendList(0))
                        import FriendListProtocol._
                        import spray.json._
                        import spray.util._
                        val ret = response.toJson.prettyPrint
                        //val payload = Wall("name",response.wall)
                        //val x = payload.toJson
                         requestContext.complete(ret)           
                        }                  
                    } //onSuccess
                  
              }~
              path("retJson"){
                    respondWithMediaType(`application/json`){
                      /*val p1 = mypost("A",2)
                      val p2 = mypost("B",3)
                      complete{
                        import personProtocol._
                        import spray.json._
                        import spray.util._
                        Person("hello",Array(p1,p2))*/
                      val p1 = WallPost("direct",234,567,"shjjk","hjjk")
                      val p2 = WallPost("indirect",456,768,"ljjk","hhhh")
                      complete{
                        import MyJsonProtocol._
                        import spray.json._
                        import spray.util._
                        Wall("saumitra",Array(p1,p2))
                      }
                    }
              }~
              path("updateProfile" / IntNumber){(uid)=>
                post{
                      import myProfileProtocol._
                      entity(as[myProfile]){profile=>
                      respondWithMediaType(`application/json`){
                      /*println(profile.uid)
                      println(profile.name)
                      println(profile.sex)
                      println(profile.location)
                      println(profile.age)*/
                      graph(uid%numNodes) ! profile
                      complete{
                        /*
                        import spray.json._
                        import spray.util._
                        myProfile(5,"saumitra","male","Gainesville",23)*/
                        "Profile updated for "+uid.toString()
                      }
                    }
                  }
                }
              
              }~
            path("getProfile" / IntNumber) {(uid)=>
                requestContext =>
                 
                  implicit val timeout = new Timeout(5 seconds)
                  val a_sys = context.system
                  import a_sys.dispatcher
                  (graph(uid%numNodes) ? getProfile(uid))
                    .recover{
                    case ex:AskTimeoutException =>{
                      requestContext.complete("Request Timed out")
                      }
                      
                    }
                      .onSuccess{
                      case result => {
                        val response = result.asInstanceOf[myProfile]
                        import myProfileProtocol._
                        import spray.json._
                        import spray.util._
                        val ret = response.toJson.prettyPrint
                        //val payload = Wall("name",response.wall)
                        //val x = payload.toJson
                         println("GetProfileRequestReceived")
                         requestContext.complete(ret)           
                        }                  
                    } //onSuccess
                  
              }~
            path("createPage"){
                parameters("name".as[String],"topic".as[String],"pid".as[Int],"uid".as[Int]){(name,topic,pid,uid)=>
                graph(pid%numNodes) ! createPage(name,topic,pid,uid)
                complete{
                    "Page creation request submitted to System"
                  }
                }
              }~
            path("getPage" / IntNumber) {(pid)=>
                requestContext =>
                  implicit val timeout = new Timeout(5 seconds)
                  val a_sys = context.system
                  import a_sys.dispatcher
                  (graph(pid%numNodes) ? getPage(pid))
                    .recover{
                    case ex:AskTimeoutException =>{
                      requestContext.complete("Request Timed out")
                      }
                      
                    }
                      .onSuccess{
                      case result => {
                        val response = result.asInstanceOf[ret_page]
                        import myPageProtocol._
                        import spray.json._
                        import spray.util._
                        val ret = response.toJson.prettyPrint
                        //val payload = Wall("name",response.wall)
                        //val x = payload.toJson
                         println("GetPageRequestReceived")
                         requestContext.complete(ret)           
                        }                  
                    } //onSuccess
                  
              }~
           path("postToPage"){
                parameters("pid".as[Int],"uid".as[Int],"post".as[String]){(pid,uid,post)=>
                graph(pid%numNodes) ! postToPage(pid,uid,post)
                complete{
                    "Post to page submitted to System"
                  }
                }
              }
        }
        
         
      })
}
