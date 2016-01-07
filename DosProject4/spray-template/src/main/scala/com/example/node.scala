package com.example
import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import scala.collection.mutable.ArrayBuffer
import collection.mutable.HashMap
import spray.routing.RequestContext
import spray.httpx.SprayJsonSupport._
import spray.http.MediaTypes
/*
 * Every node represents a a chunk of users in the system
 * that is every node is responsible for maintaining
 * data-structures that store the state of a particular
 * user.
 * Since we do not have a back-end database this model makes
 * sense to manage consistency.
 * */
import spray.json._
import DefaultJsonProtocol._ 
case class sendPost(sender_uid:Int,receiver_uid:Int,post:String)
case class updateStatus(uid:Int,post:String)
case class postOnWall(sender_uid:Int,receiver_uid:Int,post:String,msg_type:String,first_receiver_uid:Int)
case class WriteWall(sender_uid:Int,receiver_uid:Int,post:String)
case class getWall(uid:Int)
case class requestStatus(status:String)
case class returnWall(status:String,wall:Array[WallPost])
case class registerUser(uid:Int,name:String)
case class configure(graphMap:ArrayBuffer[ActorRef])
case class forgeFriendship(initiator_uid:Int,target_uid:Int) // Goes from client to FrontEnd
case class sealFriendShip(target_uid:Int,initiator_uid:Int) // goes from node A to B
case class getFriends(uid:Int)
case class returnFriendList(friendList:Array[Int])
case class WallPost(msg_type:String,submitterUID:Int,first_receiver_uid:Int,content:String,timeStamp:String)
case class getProfile(uid:Int)
case class retProfile(self_profile:myProfile)
case class createPage(name:String,topic:String,pid:Int,uid:Int)
case class getPage(pid:Int)
case class page_post(submitter_uid:Int,post:String,timestamp:String)
case class ret_page(name:String,topic:String,creator_uid:String,created:String,contents:Array[page_post])
case class postToPage(pid:Int,uid:Int,post:String)


class node extends Actor
{
  // every node should know of every other node
  // in the system to send messages/posts to 
  // other user's wall.
  var graph:ArrayBuffer[ActorRef]=null
  // A set of data-structures that will hold
  // state of users
  val myMembers = new HashMap[Int,user]() { override def default(key:Int) = null }
  val myPages = new HashMap[Int,page]() { override def default(key:Int) = null }
  /*
   * Messages to be handled by a node
   * putPosts
   * getWall
   * register
   * getProfile
   * getFriends
   * */
  def receive=
  {
    case configure(graphMap:ArrayBuffer[ActorRef])=>
    {
      graph=graphMap
      println("configured----"+self.path.name)
    }
    case registerUser(uid:Int,name:String)=>
      {
        myMembers+=(uid->new user(name,uid))
        myMembers(uid).setProfile(myProfile(uid,name,"Male","USA",20))
        println("registerUser----"+self.path.name)
        sender ! requestStatus("ok")
      }
    case forgeFriendship(initiator_uid:Int,target_uid:Int)=>
      {
        /* Sent from front-end*/
        if (myMembers(initiator_uid)!=null)
        {
          myMembers(initiator_uid).addFriend(target_uid)
          println("---FriendShip Forged---"+initiator_uid.toString()+"->"+target_uid.toString())
        }
        /*Send message to target UID*/
         graph(target_uid%graph.length) ! sealFriendShip(target_uid,initiator_uid)
      }
    case sealFriendShip(initiator_uid:Int,target_uid:Int)=>
      {
        /*Sent from Initiator*/
        if (myMembers(initiator_uid)!=null)
        {
          myMembers(initiator_uid).addFriend(target_uid)
          println("---FriendShip Forged---"+initiator_uid.toString()+"->"+target_uid.toString())
        }
      }
    case updateStatus(uid:Int,post:String)=>
      {
        /*This has to go on my Wall and wall of my Friends*/
        if (myMembers(uid)!=null)
        {
          myMembers(uid).postWall("direct",uid,-9999,post)
          /*Now write to wall of my friends*/
          for (i <- 0 until myMembers(uid).friendIndex)
          {
            val receiver_uid = myMembers(uid).friendList(i)
            graph(receiver_uid%graph.length) ! postOnWall(uid,receiver_uid,post,"direct",-9999)
          }         
          sender ! requestStatus("ok")
        }
        else
        {
          sender ! requestStatus("User with this UID does not exists.")
        }
      }
    case sendPost(sender_uid:Int, first_receiver_uid:Int,post:String)=>
      {
        /*This is a msg sent from A->B , should be visible on
         * wall of each of A's and B's friends in addition to 
         * A's and B's wall.*/
        /* post on sender's wall*/
        if (myMembers(sender_uid)!=null) // this is my UID
        {
          /* post on sender's wall*/
          myMembers(sender_uid).postWall("Indirect",sender_uid,first_receiver_uid, post)
          /* post on wall of my friends*/
          for (i <- 0 until myMembers(sender_uid).friendIndex)
          {
            val friend_uid = myMembers(sender_uid).friendList(i)
            if (friend_uid != first_receiver_uid) // avoids duplicate messaging
            {
              graph(friend_uid%graph.length) ! postOnWall(sender_uid,friend_uid,post,"Indirect",first_receiver_uid)
            }
          }  
          /*Send a message to the first_receiver*/         
          graph(first_receiver_uid%graph.length) ! WriteWall(sender_uid,first_receiver_uid,post)
        }
      }
    case WriteWall(sender_uid:Int,receiver_uid:Int,post:String)=>
      {
        /*This is a post on my Wall by my Friend*/
        if (myMembers(receiver_uid)!=null && myMembers(receiver_uid).isFriend(sender_uid)==true)
        {
          myMembers(receiver_uid).postWall("direct",sender_uid,-9999, post)
          /*Let my friends know I received a post on my Wall*/
          for (i <- 0 until myMembers(receiver_uid).friendIndex)
          {
            val friend_uid = myMembers(receiver_uid).friendList(i)
            if (friend_uid != sender_uid) // avoid loop messaging
            {
              graph(friend_uid%graph.length) ! postOnWall(sender_uid,friend_uid,post,"Indirect",receiver_uid)
            }
          }  
           sender ! requestStatus("ok")
        }
        else
        {
          sender ! requestStatus("User with this UID does not exists.")
        }
      }
    case postOnWall(sender_uid:Int,receiver_uid:Int,post:String,msg_type:String,first_receiver_uid:Int)=>
      {
        if (myMembers(receiver_uid)!=null)
        {
          myMembers(receiver_uid).postWall(msg_type,sender_uid,first_receiver_uid, post)
          sender ! requestStatus("ok")
        }
        else
        {
          sender ! requestStatus("User with this UID does not exists.")
        }
      }
    case getWall(uid:Int)=>
      {
        if (myMembers(uid)!=null)
        {
          val retWall = myMembers(uid).wall.filter(_.submitterUID>0)
          println("getWall----"+self.path.name)
          sender ! returnWall("Wall is ",retWall)                                                                              
          
        }
        else
        {
          sender ! returnWall("User with this UID does not exists.",null)
        }
      }
    case getFriends(uid:Int)=>
      {
        val friendList = myMembers(uid).friendList.filter(_ > 0)
        sender ! returnFriendList(friendList)
      }
    case myProfile(uid:Int,name:String,sex:String,location:String,age:Int)=>
      {
        val profile = myProfile(uid,name,sex,location,age)
        if (myMembers(uid)!=null)
        {
          myMembers(uid).setProfile(profile)
        }
      }
    case getProfile(uid:Int)=>
      {
        if (myMembers(uid)!=null)
        {
          sender ! myMembers(uid).getProfile()
        }
      }
    case createPage(name:String,topic:String,pid:Int,uid:Int)=>
      {
        myPages+=(pid->new page(name,topic,pid,uid))
        println("created new page----"+self.path.name)
        sender ! requestStatus("ok")
      }
    case getPage(pid:Int)=>
    {
      if (myPages(pid) != null)
      {
        var req_page = myPages(pid)
        val name = req_page.page_name
        val topic = req_page.about
        val creator = req_page.creator_id.toString()
        val created = req_page.created
        val contents = req_page.contents.filter(_.submitter_uid>0)
        sender ! ret_page(name,topic,creator,created,contents)
        /*case class page_post(submitter_uid:Int,post:String,timestamp:String)
case class ret_page(name:String,topic:String,creator_uid:String,created:String,contents:Array[page_post])*/
        
      }
    }
    case postToPage(pid:Int,uid:Int,post:String)=>
    {
      if (myPages(pid) != null)
      {
        myPages(pid).postPage(uid,post)
      }
        
    }
      
  }
  
  
}