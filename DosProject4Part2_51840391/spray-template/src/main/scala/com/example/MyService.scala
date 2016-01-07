package com.example

import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import spray.routing._
import spray.http._
import MediaTypes._
import scala.collection.mutable.ArrayBuffer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.pattern.{ after, ask, pipe }
import akka.pattern.AskTimeoutException
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.http.MediaTypes
import spray.httpx.SprayJsonSupport._
import spray.routing.RequestContext

case class registerMsg(name:String,uid:Int,pubKey:String)
object registrationProtocol extends DefaultJsonProtocol {
  implicit val registrationFormat = jsonFormat3(registerMsg) 
}
case class registerMsgWrapper(msg:registerMsg,context:RequestContext)
case class Wall(name: String, items: Array[WallPost])
object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val WallPostFormat = jsonFormat5(WallPost)
  implicit val WallFormat = jsonFormat2(Wall)
}

case class secureWall(uid:Int,items:Array[secureWallPost])
object secureWallProtocol extends DefaultJsonProtocol{
  implicit val secureWallPostFormat = jsonFormat5(secureWallPost)
  implicit val secureWallFormat = jsonFormat2(secureWall)
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

object secureFriendListProtocol extends DefaultJsonProtocol{
  implicit val secureFriendListFormat = jsonFormat1(returnSecureFriendList)
}

case class secureFriendListRequest(uid:Int,digiSign:String)
object secureFriendListRequestProtocol extends DefaultJsonProtocol{
  implicit val secureFriendListRequestFormat = jsonFormat2(secureFriendListRequest)
}

case class SecureStatusUpdate(myUID:Int,timestamp:String,payload:String,iv:String,digiSign:String,keys:Array[(Int,String)])
object SecureStatusUpdateProtocol extends DefaultJsonProtocol{
  implicit val SecureStatusUpdateFormat = jsonFormat6(SecureStatusUpdate)
}
case class SecureStatusUpdateWrapper(update:SecureStatusUpdate,context:RequestContext)

case class SecureWriteOnWall(targetUID:Int,myUID:Int,payload:String,timestamp:String,iv:String,encKey:String,digiSign:String)
object SecureWriteOnWallProtocol extends DefaultJsonProtocol{
  implicit val SecureWriteOnWallFormat = jsonFormat7(SecureWriteOnWall)
}
case class SecureWriteOnWallWrapper(post:SecureWriteOnWall,context:RequestContext)

case class createSecurePage(name:String,topic:String,pid:Int,creator_uid:Int,digiSign:String)
object createSecurePageProtocol extends DefaultJsonProtocol{
  implicit val createSecurePageFormat = jsonFormat5(createSecurePage)
}
case class createSecurePageWrapper(pageCreationReq:createSecurePage,context:RequestContext)

case class subscribeToPage(requestor_uid:Int,owner_uid:Int,page_Id:Int,requestor_pubKey:String)
object subscribeToPageProtocol extends DefaultJsonProtocol{
  implicit val subscribeToPageFormat = jsonFormat4(subscribeToPage)
}
case class subscribeToPageWrapper(subscription_req:subscribeToPage,context:RequestContext)

case class getSubscriptionList(requestor_uid:Int,owner_uid:Int,page_id:Int,digiSign:String)
object getSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val getSubscriptionListFormat = jsonFormat4(getSubscriptionList)
}
case class getSubscriptionListWrapper(subscriptionList_req:getSubscriptionList)

case class retSubscriptionList(subscribers:Array[(Int,String)])
object retSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val retSubscriptionListFormat = jsonFormat1(retSubscriptionList)
}

case class securePostToPage(page_post:secure_page_post,page_id:Int,owner_id:Int,digiSign:String)
object securePostToPageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val securePostToPageFormat = jsonFormat4(securePostToPage)
}
case class securePostToPageWrapper(post:securePostToPage,requestContext:RequestContext)

case class getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)

case class ret_secure_page(name:String,topic:String,creator_uid:Int,created:String,contents:Array[secure_page_post])
object ret_secure_pageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val ret_secure_pageFormat = jsonFormat5(ret_secure_page)
}

case class myProfile(uid:Int,name:String,sex:String,location:String,age:Int,publicKey:String)
object myProfileProtocol extends DefaultJsonProtocol{
  implicit val myProfileFormat = jsonFormat6(myProfile)
}

object myPageProtocol extends DefaultJsonProtocol{
  implicit val pagePost = jsonFormat3(page_post)
  implicit val myPage = jsonFormat5(ret_page)
}

case class friendRequest(initiator_uid:Int, target_uid:Int,digital_signature:String)
object friendRequestProtocol extends DefaultJsonProtocol{
  implicit val friendReq = jsonFormat3(friendRequest)
}

case class friendRequestWrapper(frnd_req:friendRequest,context:RequestContext)

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
          path("registration"){
                post{
                      import registrationProtocol._
                      entity(as[registerMsg]){msg=>
                      requestContext =>
                      val uid = msg.uid
                      graph(uid%numNodes) ! registerMsgWrapper(msg,requestContext)
                    
                  }
                }
              
              }~
          path("friendRequest"){
                post{
                      import friendRequestProtocol._
                      entity(as[friendRequest]){frnd_req=>
                      requestContext =>
                      val initiator_uid = frnd_req.initiator_uid
                      graph(initiator_uid%numNodes) ! friendRequestWrapper(frnd_req,requestContext)                   
                  }
                }              
              }~
          /*path("retJson"){
                    respondWithMediaType(`application/json`){
                      get{
                        import secureFriendListRequestProtocol._
                        entity(as[secureFriendListRequest]){frnd_list_req=>
                          {
                            val uid = frnd_list_req.uid
                            println(uid)
                            implicit val timeout = new Timeout(5 seconds)
                            val a_sys = context.system
                            import a_sys.dispatcher
                            val response:Future[returnSecureFriendList]=(graph(uid%numNodes) ? getSecureFriends(frnd_list_req))
                            response.onComplete{
                              case Success(response:returnSecureFriendList)=>
                                {
                                  import secureFriendListProtocol._
                                  import spray.json._
                                  import spray.util._
                                  complete{response}
                                }
                               case Failure(e) => println("could not get friend list.")
                            }
                          }
                        }
                      }
                    }
              }~*/
          path("SecureFriendList") {
                get{
                  import secureFriendListRequestProtocol._
                  entity(as[secureFriendListRequest]){frnd_list_req=>
                  requestContext =>
                    val uid = frnd_list_req.uid
                    println(uid)
                    implicit val timeout = new Timeout(5 seconds)
                    val a_sys = context.system
                    import a_sys.dispatcher
                    (graph(uid%numNodes) ? getSecureFriends(frnd_list_req))
                      .recover{
                      case ex:AskTimeoutException =>{
                        requestContext.complete("Request Timed out")
                        }
                        
                      }
                        .onSuccess{
                        case result => {
                          import secureFriendListProtocol._
                          import spray.json._
                          import spray.util._
                          val response = result.asInstanceOf[returnSecureFriendList]
                          println("FriendListRequestReceived")
                          val ret = response.toJson.toString
                          //val payload = Wall("name",response.wall)
                          //val x = payload.toJson
                          println(response.friendList(0)._1)
                          requestContext.complete(HttpResponse(entity=HttpEntity(`application/json`, ret)))           
                          }                  
                      } //onSuccess
                    }
                }//get 
              }~
          path("secureStatusUpdate"){
                post{
                      import SecureStatusUpdateProtocol._
                      //SecureStatusUpdate(myUID:Int,timestamp:String,payload:String,iv:String,digiSign:String,keys:Array[(Int,String)])
                      entity(as[SecureStatusUpdate]){update=>
                      requestContext =>
                      val initiator_uid = update.myUID
                      graph(initiator_uid%numNodes) ! SecureStatusUpdateWrapper(update,requestContext)                   
                  }
                }              
              }~
          path("secureWriteOnWall"){
                post{
                      import SecureWriteOnWallProtocol._
                      //SecureStatusUpdate(myUID:Int,timestamp:String,payload:String,iv:String,digiSign:String,keys:Array[(Int,String)])
                      entity(as[SecureWriteOnWall]){post=>
                      requestContext =>
                      val target_uid = post.targetUID
                      graph(target_uid%numNodes) ! SecureWriteOnWallWrapper(post,requestContext)                   
                  }
                }              
              }~
              /* Any body can see my wall , but everything is encrypted so it is useless unless he has the keys*/
          path("showSecureWall" / IntNumber) {(uid)=>
                get{
                    requestContext =>
                    println(uid)
                    implicit val timeout = new Timeout(5 seconds)
                    val a_sys = context.system
                    import a_sys.dispatcher
                    (graph(uid%numNodes) ? getSecureWall(uid))
                      .recover{
                      case ex:AskTimeoutException =>{
                        requestContext.complete("Request Timed out")
                        }
                        
                      }
                        .onSuccess{
                        case result => {
                          import secureWallProtocol._
                          import spray.json._
                          import spray.util._
                          val response = result.asInstanceOf[secureWall]
                          println("showSecureWallrequestReceived")
                          val ret = response.toJson.toString
                          //val payload = Wall("name",response.wall)
                          //val x = payload.toJson
                          println(ret)
                          requestContext.complete(HttpResponse(entity=HttpEntity(`application/json`, ret)))           
                          }                  
                      } //onSuccess
                }//get 
              }~
           path("createSecurePage"){
              post{
                      import createSecurePageProtocol._
                      entity(as[createSecurePage]){pageCreationRequest=>
                      requestContext =>
                      val uid = pageCreationRequest.creator_uid
                      graph(uid%numNodes) ! createSecurePageWrapper(pageCreationRequest,requestContext)
                    
                  }
                }
            }~
          path("subscribePage"){
              post{
                      import subscribeToPageProtocol._
                      entity(as[subscribeToPage]){subscriptionRequest=>
                      requestContext =>
                      val uid = subscriptionRequest.owner_uid
                      graph(uid%numNodes) ! subscribeToPageWrapper(subscriptionRequest,requestContext)
                    
                  }
                }
            }~
           path("subscriptionList") {
                get{
                  import getSubscriptionListProtocol._
                  entity(as[getSubscriptionList]){subscriptionListRequest=>
                  requestContext =>
                    val uid = subscriptionListRequest.owner_uid
                    println(uid)
                    implicit val timeout = new Timeout(5 seconds)
                    val a_sys = context.system
                    import a_sys.dispatcher
                    (graph(uid%numNodes) ? getSubscriptionListWrapper(subscriptionListRequest))
                      .recover{
                      case ex:AskTimeoutException =>{
                        requestContext.complete("Request Timed out")
                        }
                        
                      }
                        .onSuccess{
                        case result => {
                          import retSubscriptionListProtocol._
                          import spray.json._
                          import spray.util._
                          val response = result.asInstanceOf[retSubscriptionList]
                          println("FriendListRequestReceived")
                          val ret = response.toJson.toString
                          //val payload = Wall("name",response.wall)
                          //val x = payload.toJson
                          println(response.subscribers(0)._1)
                          requestContext.complete(HttpResponse(entity=HttpEntity(`application/json`, ret)))           
                          }                  
                      } //onSuccess
                    }
                }//get 
              }~
           path("securePagePost"){
                post{
                      import securePostToPageProtocol._
                      //SecureStatusUpdate(myUID:Int,timestamp:String,payload:String,iv:String,digiSign:String,keys:Array[(Int,String)])
                      entity(as[securePostToPage]){post=>
                      requestContext =>
                      val owner_uid = post.owner_id
                      graph(owner_uid%numNodes) ! securePostToPageWrapper(post,requestContext)                   
                  }
                }              
              }~
           path("showSecurepage" / IntNumber / IntNumber / IntNumber) {(pageID,ownerID,requestorID)=>
                get{
                    requestContext =>
                    println(ownerID)
                    implicit val timeout = new Timeout(5 seconds)
                    val a_sys = context.system
                    import a_sys.dispatcher
                    (graph(ownerID%numNodes) ? getSecurePage(pageID,ownerID,requestorID))
                      .recover{
                      case ex:AskTimeoutException =>{
                        requestContext.complete("Request Timed out")
                        }
                        
                      }
                        .onSuccess{
                        case result => {
                          import ret_secure_pageProtocol._
                          import spray.json._
                          import spray.util._
                          val response = result.asInstanceOf[ret_secure_page]
                          println("ret_secure_page-requestReceived")
                          val ret = response.toJson.toString
                          //val payload = Wall("name",response.wall)
                          //val x = payload.toJson
                          println(ret)
                          requestContext.complete(HttpResponse(entity=HttpEntity(`application/json`, ret)))           
                          }                  
                      } //onSuccess
                }//get 
              }~
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




/*
 *  
 *  path("SecureFriendList") {
                get{
                  import secureFriendListRequestProtocol._
                  entity(as[secureFriendListRequest]){frnd_list_req=>
                  requestContext =>
                    val uid = frnd_list_req.uid
                    println(uid)
                    implicit val timeout = new Timeout(5 seconds)
                    val a_sys = context.system
                    import a_sys.dispatcher
                    (graph(uid%numNodes) ? getSecureFriends(frnd_list_req))
                      .recover{
                      case ex:AskTimeoutException =>{
                        requestContext.complete("Request Timed out")
                        }
                        
                      }
                        .onSuccess{
                        case result => {
                          import secureFriendListProtocol._
                          import spray.json._
                          import spray.util._
                          val response = result.asInstanceOf[returnSecureFriendList]
                          println("FriendListRequestReceived")
                          val ret = response.toJson.prettyPrint
                          //val payload = Wall("name",response.wall)
                          //val x = payload.toJson
                          println(response.friendList(0)._1)
                           requestContext.complete(response)           
                          }                  
                      } //onSuccess
                    }
                }//get 
              }~
 */
