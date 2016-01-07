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
import spray.http._
import spray.client.pipelining._
import scala.util.{ Success, Failure }
import collection.mutable.HashMap
import scala.util.Random
import java.util.Date
import java.util.Calendar
import java.text._
import scala.concurrent.duration.DurationInt

case object action
case object setUsers
case object setFriends
case object setUsersAck
case object setFriendsAck
case object kickStart
case object getWall
case object makePost
case object randomTrigger
case class registerUser(uid:Int)
case class updateStatus() // update status for this user
case class makeFriends() // choose a randomFriend for this user and send it request
case object updateProfile
case object getProfile
case class requests(status_updates:Int,posts:Int,checkWall:Int,makeFriend:Int,
    updateProfile:Int,getProfile:Int,total:Int)
case object stop
case object sendStats

case class myProfile(uid:Int,name:String,sex:String,location:String,age:Int)
object myProfileProtocol extends DefaultJsonProtocol{
  implicit val myProfileFormat = jsonFormat5(myProfile)
}

class Statistics(status_updates:Int,posts:Int,checkWall:Int,makeFriend:Int,
    updateProfile:Int,getProfile:Int,total:Int)
    {
      var statusUpdates = status_updates
      var wall_posts = posts
      var timeline_views = checkWall
      var friendRequests = makeFriend
      var profileUpdates = updateProfile
      var profileViews = getProfile
      var net_requests = total
    }

/*case object start

case class myProfile(name:String,sex:String,location:String,age:Int)
object myProfileProtocol extends DefaultJsonProtocol{
  implicit val myProfileFormat = jsonFormat4(myProfile)
}

class Master extends Actor
{
  val uri = "http://localhost:8080/graph/myProfile"
  def receive =
  {
    case `start`=>
      {
         implicit val timeout = new Timeout(5 seconds)
         val a_sys = context.system
         import a_sys.dispatcher
         import myProfileProtocol._
         import spray.json._
         import spray.util._
         val pipeline = addHeader("Accept", "application/json") ~> sendReceive ~> unmarshal[myProfile]
         val response = pipeline{Post(uri, myProfile("adam","male","London",20))}
         response onComplete {
            case Success(result) =>
              println(result.name)
       
            case Failure(error) =>
              println("Error")
          }
         println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
      }
  }
}

object Client {
  
  def main(args: Array[String]){
    implicit val system = ActorSystem("social-client")
    val client1 = system.actorOf(Props(new Master()),"Master")
    client1 ! start
    
  }
}*/

object utils{
  val maxUsers = 99999
  val base_uri = "http://localhost:8080/graph"
  val register_uri = base_uri + "/register" // 1
  val updateStatus_uri = base_uri + "/updateStatus" // 2
  val makeFriends_uri = base_uri + "/makeFriends" // 3
  val makePost_uri = base_uri + "/sendPost" // 4
  val friendList_uri = base_uri + "/friendList"// 5
  val wall_uri = base_uri + "/getWall"// 6
  val setProfile_uri = base_uri +"/updateProfile" //7
  val getProfile_uri = base_uri + "/getProfile" //8
  val options = Array("updateStatus","makeFriends","makePost","friendList","getWall")
}


class Master(myClients:ArrayBuffer[ActorRef]) extends Actor
{
  var requestArray = Array.fill[Int](6)(0) // keeps track of each kind of requests
  var responseArray = Array.fill[Int](6)(0) // keeps track of successful responses
  var setUsersIndex = 0
  var setFriendsIndex = 0
  var requestReport = 0
  var statistics = new Statistics(0,0,0,0,0,0,0)
  var start_time = System.currentTimeMillis
  var stop_time = start_time
  def receive=
  {
    case `action`=>
      {
        for (i <-0 until myClients.length)
        {
          myClients(i) ! setUsers
        }
      }
    case `setUsersAck` =>
      {
        setUsersIndex+=1
        if (setUsersIndex == myClients.length)
        {
          for (i <-0 until myClients.length)
          {
            myClients(i) ! setFriends
          }
        }
      }
    case `setFriendsAck`=>
      {
        setFriendsIndex+=1
        if (setFriendsIndex == myClients.length)
        {
          for (i <-0 until myClients.length)
          {
            myClients(i) ! kickStart
          }
        }
        
      }
    case `stop`=>
      {
        for (i <-0 until myClients.length)
          {
            myClients(i) ! sendStats
          }
        
      }
    case requests(status_updates:Int,posts:Int,checkWall:Int,makeFriend:Int,updateProfile:Int,getProfile:Int,total:Int)=>
      {
        requestReport+=1
        statistics.statusUpdates += status_updates
        statistics.wall_posts +=posts
        statistics.timeline_views+=checkWall
        statistics.friendRequests+=makeFriend
        statistics.profileUpdates+=updateProfile
        statistics.profileViews+=getProfile
        statistics.net_requests+=total
        
        if (requestReport == myClients.length)
        {
          stop_time = System.currentTimeMillis
          val run_duration = (stop_time - start_time)/1000
          /*Print Stats--then stop system*/
          println("######################STATISTICS################################")
          println("The statistics gathered from this run as as below--")
          println("Status-Updates "+statistics.statusUpdates)
          println("Posts on other's Walls "+statistics.wall_posts)
          println("Checking Timeliness "+statistics.timeline_views)
          println("FriendRequest "+statistics.friendRequests)
          println("ProfileUpdates "+statistics.profileUpdates)
          println("ProfileViews "+statistics.profileViews)
          println("Net Requests "+statistics.net_requests)
          println("Run_Duration in Seconds "+run_duration.toString())
          println("Requests handled per second "+ (statistics.net_requests/run_duration).toString())
          println("###############################################################")
          requestReport = 0
          statistics.statusUpdates = 0
          statistics.wall_posts =0
          statistics.timeline_views=0
          statistics.friendRequests=0
          statistics.profileUpdates=0
          statistics.profileViews=0
          statistics.net_requests=0
          /* stop system*/
          //context.system.shutdown()
          /*for (i <-0 until myClients.length)
          {
            myClients(i) ! PoisonPill
          }*/
        }
      }

  } 
}

class Client(fromID:Int,toID:Int,num_pipelines:Int) extends Actor
{
  /*Each Client here must make requests on behalf of a certain number of 
   * users to simulate them.*/
  /* Keeps track of which users in my range have been registered*/
  var Master:ActorRef = null
  val myUsers = new HashMap[Int,Boolean]() { override def default(key:Int) = false }
  val myUsersFriends = new HashMap[Int,ArrayBuffer[Int]]() { override def default(key:Int) = new ArrayBuffer[Int] }
  val alias_rnd = new scala.util.Random
  val range = fromID to toID
  val a_sys = context.system
  import a_sys.dispatcher
  import spray.json._
  import spray.util._
  var local_stats = new Statistics(0,0,0,0,0,0,0)
  val pipeline1 = sendReceive 
  val pipeline2 = sendReceive
  val pipeline3 = sendReceive
  val pipeline4 = sendReceive
  val pipeline5 = sendReceive
  val pipeline6 = sendReceive
  val pipeline7 = sendReceive
  val pipeline_setprofile = addHeader("Accept", "application/json") ~> sendReceive 
  val pipeline_getprofile = sendReceive
  val pipelines = List.fill(num_pipelines)(sendReceive)
  //val pipelines = Array(pipeline1,pipeline2,pipeline3,pipeline4,pipeline5,pipeline6,pipeline7)
  def receive =
    {
    case `kickStart`=>
      {
        /*Depending on what users do on Facebook we can set 
         * the frequency at which a user invokes a request
         * to RestAPI
         * based on common case patterns the most common activity on face-book*/
        Master = sender
        val a_sys = context.system
        import a_sys.dispatcher
        /* Update Status*/
        a_sys.scheduler.schedule(new FiniteDuration(10,MILLISECONDS),new FiniteDuration(20,MILLISECONDS),self,updateStatus())
        /* Check your Wall*/
        a_sys.scheduler.schedule(new FiniteDuration(20,MILLISECONDS),new FiniteDuration(25,MILLISECONDS),self,getWall)
        /* Make a new Friend, although not frequent required for Simulation*/
        a_sys.scheduler.schedule(new FiniteDuration(40,MILLISECONDS),new FiniteDuration(30,MILLISECONDS),self,makeFriends())
        /* Make post to a Friend's Wall */
        a_sys.scheduler.schedule(new FiniteDuration(80,MILLISECONDS),new FiniteDuration(25,MILLISECONDS),self,makePost)
        /* Update profile*/
        a_sys.scheduler.schedule(new FiniteDuration(60,MILLISECONDS),new FiniteDuration(250,MILLISECONDS),self,updateProfile)
        /* getProfile*/
        a_sys.scheduler.schedule(new FiniteDuration(100,MILLISECONDS),new FiniteDuration(30,MILLISECONDS),self,getProfile)
        /*  get statistics periodically*/
        a_sys.scheduler.schedule(new FiniteDuration(10,SECONDS),new FiniteDuration(10,SECONDS),self,sendStats)

      }
    
    case `setUsers`=>
      {
        /*Frame request and handle response*/
        implicit val timeout = new Timeout(5 seconds)
        for (i <- fromID to toID)
        {
           val uri = utils.register_uri+"/"+i.toString()+"/Actor"+i.toString()
           val mypipeline = pipelines(i%pipelines.length)
           val response = mypipeline{Post(uri)}
           response onComplete {
            case Success(result) =>
              {
                myUsers(i)=true
                myUsersFriends+=(i->ArrayBuffer[Int]())
                println("Registered user "+ i.toString())
              }
       
            case Failure(error) =>
              println("Error registering User")
          }
        }
        sender ! setUsersAck
      }
      
    case updateStatus()=>
      {
        /*Frame a Status*/
        val uid = (range(alias_rnd.nextInt(range length)))
        val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        val timestamp = new Date()
        var status = "This is status of "+ uid.toString() + " at "+ timestamp.toString()
        status = status.replace(" ","_")
        implicit val timeout = new Timeout(5 seconds)
        val uri = utils.updateStatus_uri+"?uid="+uid.toString()+"&status="+status
        val mypipeline = pipelines(uid%pipelines.length)
        val response = mypipeline{Post(uri)}
         response onComplete {
            case Success(result) =>
              {
                println("Status was updated by "+ uid.toString())
                local_stats.statusUpdates+=1
                local_stats.net_requests+=1
              }
       
            case Failure(error) =>
              println("Error updating Status")
          }
      } 
    case `getWall`=>
      {
         val uid = (range(alias_rnd.nextInt(range length)))
         implicit val timeout = new Timeout(5 seconds)
         val uri = utils.wall_uri+"/"+uid.toString()
         val mypipeline = pipelines(uid%pipelines.length)
         val response = mypipeline{Post(uri)}
         response onComplete {
           case Success(result) =>
            {
                println(result)
                local_stats.timeline_views+=1
                local_stats.net_requests+=1
            }
            case Failure(error) =>
              println("Cannot get Wall")
          } 
      }
    case `makePost`=>
      {
        val myUID = (range(alias_rnd.nextInt(range length)))
        val friendList = myUsersFriends(myUID)
        if (friendList.length != 0)
        {
          println("MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM")
          val friendUID = (friendList(alias_rnd.nextInt(friendList length)))
          val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
          val timestamp = new Date()
          implicit val timeout = new Timeout(5 seconds)
          var post = "This is post from "+ myUID.toString() + " at "+ timestamp.toString() + " to " + friendUID.toString()
          post = post.replace(" ","_")
          val uri = utils.makePost_uri+"?sender="+myUID.toString()+"&receiver="+friendUID.toString()+"&post="+post
          println("URI "+uri)
          //val mypipeline = pipelines(myUID%pipelines.length)
          val mypipeline = pipeline7
          val response = mypipeline{Post(uri)}
           response onComplete {
           case Success(result) =>
              {
                  println(result)
                  local_stats.wall_posts+=1
                  local_stats.net_requests+=1
                  println("SSSSSSSSSSSSSSSSSSSSSSSSSSS")
              }
            case Failure(error) =>
              println("Cannot submit post to the System")
          } 
        }
      }
    case makeFriends()=>
      {
        val uid = (range(alias_rnd.nextInt(range length)))
        val friendUID = (range(alias_rnd.nextInt(range length)))
        //vectVisitedPoint.exists(_ == (value1, value2))
        val friendList = myUsersFriends(uid)
        if (friendList.exists(_== friendUID))
        {
          // Do nothing Already Friends
        }
        else
        {
          val uri = utils.makeFriends_uri+"/"+uid.toString()+"/"+friendUID.toString()
          val mypipeline = pipelines(uid%pipelines.length)
          val response = mypipeline{Post(uri)}
          response onComplete {
            case Success(result) =>
              {
                myUsersFriends(uid).+=(friendUID)
                local_stats.friendRequests+=1
                local_stats.net_requests+=1
                println("Friend request sent from "+ uid.toString() + " to "+friendUID.toString())
              }
            case Failure(error) =>
              println("Error sending FriendRequest")
          }
        }        
      }
    case `setFriends`=>
      {
        /* For initialization we set things up in such a way that
         * every user has 2 friends to begin With*/
        implicit val timeout = new Timeout(5 seconds)
        val range = fromID to toID
        for (i <- fromID to toID)
        {
         for (j <- 1 to 2)
         {
           val friendUID = (range(alias_rnd.nextInt(range length)))
           val uri = utils.makeFriends_uri+"/"+i.toString()+"/"+friendUID.toString()
           val mypipeline = pipelines(i%pipelines.length)
           val response = mypipeline{Post(uri)}
            response onComplete {
            case Success(result) =>
              {
                myUsersFriends(i).+=(friendUID)
                println("Friend request sent from "+ i.toString() + " to "+friendUID.toString())
              }
            case Failure(error) =>
              println("Error sending FriendRequest")
          }
         }
        }
        sender ! setFriendsAck
      }
    case `updateProfile`=>
      {
         val uid = (range(alias_rnd.nextInt(range length)))
         implicit val timeout = new Timeout(5 seconds)
         val uri = utils.setProfile_uri+"/"+uid.toString()
         val mypipeline = pipeline_setprofile
         import myProfileProtocol._
         import spray.json._
         import spray.util._
         val response = mypipeline{Post(uri, myProfile(uid,"adam"+uid.toString(),"male","London",20))}
         response onComplete {
           case Success(result) =>
            {
                println(result)
                local_stats.profileUpdates+=1
                local_stats.net_requests+=1
            }
            case Failure(error) =>
              println("Cannot update Profile")
          } 
        
      }
       case `getProfile`=>
      {
         val uid = (range(alias_rnd.nextInt(range length)))
         implicit val timeout = new Timeout(5 seconds)
         val uri = utils.getProfile_uri+"/"+uid.toString()
         //val mypipeline = pipelines(uid%pipelines.length)
         val mypipeline = pipeline_getprofile
         val response = mypipeline{Post(uri)}
         response onComplete {
           case Success(result) =>
            {
                println(result)
                local_stats.profileViews+=1
                local_stats.net_requests+=1
                print("ZZZZZZZZZZZZZZZZZ")
            }
            case Failure(error) =>
              println("Cannot retrieve Profile")
          } 
        
      }
       case `sendStats`=>
         {
           
          println("######################LOCAL################################")
          println("The statistics gathered from this run as as below--")
          println("Status-Updates "+local_stats.statusUpdates)
          println("Posts on other's Walls "+local_stats.wall_posts)
          println("Checking Timeliness "+local_stats.timeline_views)
          println("FriendRequest "+local_stats.friendRequests)
          println("ProfileUpdates "+local_stats.profileUpdates)
          println("ProfileViews "+local_stats.profileViews)
          println("Net Requests "+local_stats.net_requests)
          println("###############################################################")
           
          Master ! requests(local_stats.statusUpdates,local_stats.wall_posts,local_stats.timeline_views,
                             local_stats.friendRequests,local_stats.profileUpdates,local_stats.profileViews,
                             local_stats.net_requests)  
                             /*var statusUpdates = status_updates
      var wall_posts = posts
      var timeline_views = checkWall
      var friendRequests = makeFriend
      var profileUpdates = updateProfile
      var profileViews = getProfile
      var net_requests = total
      
      case class requests(status_updates:Int,posts:Int,checkWall:Int,makeFriend:Int,
    updateProfile:Int,getProfile:Int,total:Int)*/
         }
    }
}

object Simulator {
  
  def main(args: Array[String]){
    implicit val system = ActorSystem("social-client")
    var workers= args(0).toInt
    var pipelines = args(1).toInt
    var myClients = ArrayBuffer[ActorRef]()
    var start = 1
    var end = start + 999
    for (i <- 1 to workers)
    {
       myClients+=system.actorOf(Props(new Client(start,end,pipelines)),i.toString())  
       start = end+1
       end = start + 999
    }
    val Master = system.actorOf(Props(new Master(myClients)),"Master")
    Master ! action
    val a_sys = system
    import a_sys.dispatcher
    a_sys.scheduler.scheduleOnce(new FiniteDuration(300,SECONDS),Master,stop)
    
  }
}