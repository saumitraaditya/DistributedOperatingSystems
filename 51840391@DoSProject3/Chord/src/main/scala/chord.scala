import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit;

//case object pass
case object pp
case class pass(ref:ActorRef)
case object begin
case object print_fingerTable
case object ack_print
case class join(ref:ActorRef)
// originator_ref is so that the node that can answer this query responds directly to the
// the node which originated the query.
// reason ---
case class find_successor(id:Int,reason:String,orginator_ref:ActorRef)
case class your_successor(successor:ActorRef,reason:String,valid:Boolean)
case class stabilize()
case class what_is_your_predecessor()
case class return_predecessor(pred:ActorRef,valid:Boolean)
case class chord_notify(ref:ActorRef)
case class fix_fingers()
case class ack()
/* Manager asks nodes to start messaging once the network has stabilized
 * after reception of num_nodes*num_msgs has been acknowledged back to 
 * manager it shuts the system down*/
case class msg(dst:Int,hop_count:Int,origin_ref:ActorRef)
case class start_messaging()
case class msg_ack(hop_count:Int)

object utils{
   val md = java.security.MessageDigest.getInstance("SHA-1")
  def getUID(length:Int,m:Int):Int={
      val R_string:String = scala.util.Random.alphanumeric.take(length).mkString
      val R_sub = (md.digest(R_string.getBytes("UTF-8")).map("%02x".format(_)).mkString).substring(34,40)
      var IntID = (Integer.parseInt(R_sub,16)%(scala.math.pow(2,m))).toInt
      IntID
    }
}

// represents a Node in the finger-table--contains start_uid and both node UID ,ActorRef for the successor
class finger_table_entry(finger_start:Int,node_uid:Int,node_ref:ActorRef)
{
  var start = finger_start
  var uid = node_uid
  var ref = node_ref
}

object project3 {
  def main (args:Array[String]){
    if (args.length < 2)
    {
      println("Please enter <num of nodes> <num of requests>")
      return
    }
    else
    {
      val numNodes = args(0).toInt
      val numRequests = args(1).toInt
      // get the min value of m that can support a network with this number of nodes.
      // which would tell us the size of finger table to maintain.
      val min_m = (scala.math.ceil(scala.math.log(numNodes)/scala.math.log(2))).toInt
      // also for the simulation we have to ensure that every node has a unique identifier.
      // to this extent we scale min_m by a scaling factor 'k'.
      val scaling_factor = 1
      val m = scaling_factor * min_m
      // now it is time to generate unique ID's for every node.
      // we use a hash map to ensure uniqueness.
      val UID = new HashMap[Int,Boolean]()  { override def default(key:Int) = false }
      //generating UIDS for numNodes
      var rs = -9999 // initial value
      for (i <- 1 to numNodes)
      {
        do
        {
          rs = utils.getUID(50,m)
        }while (UID(rs)!=false)
          UID+=(rs->true)
      }
      // just print all valid keys in the map
      // MILESTONE 1
      //UID.foreach{case(key,value)=> {if (UID(key)==true) print(key)}}
      var UIDList = ArrayBuffer[Int]()
      // UIDList contains all valid UIDs
      UID.foreach{case(key,value)=> {if (UID(key)==true) UIDList+=key}}
      // Initialize Network
      // Set up an actor system with actors representing nodes in the network
      val actor_system = ActorSystem("ChordNetwork")
      // Collection for references of nodes in the network
      var myNodes = ArrayBuffer[ActorRef]()
      // Set up individual Actors
      for (i<-UIDList)
      {
        myNodes+=actor_system.actorOf(Props(new Node(i,m)),i.toString())
      }
      val Manager = actor_system.actorOf(Props(new Net_Manager(myNodes,numNodes,numRequests)),"Manager")
      Manager ! begin
    }
  }
}
/*This class manages the initialization and teardown of the simulated network*/
class Net_Manager(Nodelist:ArrayBuffer[ActorRef],numNodes:Int,numMsgs:Int) extends Actor
{  
  var counter = 0
  var pr_counter = 0
  var hop_counter = 0
  var msg_counter = 0
  def receive = 
  {
    case `pass` =>
      {
        println("exiting")
        context.system.shutdown()
      }
    case `begin` =>
      {
        Nodelist(0) ! join(Nodelist(0))
      }
    case ack()=>
      {
        //println("********* ack recvd from "+sender.path.name)
        counter+=1
        if (counter < Nodelist.length)
          Nodelist(counter)!join(Nodelist(counter-1))
        if (counter == Nodelist.length)
          {
            val a_sys =  context.system
            import a_sys.dispatcher
            val stabilize_time = 30
            a_sys.scheduler.scheduleOnce(new FiniteDuration(stabilize_time,SECONDS),Nodelist(0),print_fingerTable)
            println("The network will take "+stabilize_time.toString + " <stabilize_time> seconds to stabilize, you might have to tweak it if your network is too large.")
            //Nodelist(0) ! print_fingerTable
          }
      }
    case `ack_print`=>
      {
        //println("********* PRINT ack recvd from "+sender.path.name)
        pr_counter+=1
        if (pr_counter < Nodelist.length)
          Nodelist(pr_counter) ! print_fingerTable
        if (pr_counter == Nodelist.length)
          {
            for (i <- 1 to Nodelist.length)
            {
              Nodelist(i-1) ! start_messaging()
            }
            //context.system.shutdown()
          }
      }
      
    case msg_ack(hop_count:Int)=>
      {
        msg_counter+=1
        hop_counter += hop_count
        if (msg_counter == (numNodes*numMsgs))
        {
          println("--------------------FINISH-------------------")
          println("Avg hops per msg is "+(hop_counter/(numNodes*numMsgs)))
          context.system.shutdown()
        }
      }
  }
  
}
/*This class represents a Chord node in the network*/
class Node(uid_uid:Int,m_m:Int) extends Actor
{
  var Manager:ActorRef = null
  val UID = uid_uid
  val m = m_m
  var predecessor:ActorRef = null
  var successor:ActorRef = null
  var pred_uid:Int = -9999
  // construct finger table
  var finger_table = ArrayBuffer[finger_table_entry]()
  var start = -9999
  for (i <- 1 to m)
  {
    var start = ((UID+scala.math.pow(2,i-1).toInt)%(scala.math.pow(2,m))).toInt
    finger_table += new finger_table_entry(start,Int.MaxValue,null)
  }
  //Print initial finger table for the node
  /*println("*****NODE "+UID.toString()+"******")
  for (i <-1 to m)
  {
    println(finger_table(i-1).start)
    println(finger_table(i-1).uid)
  }*/
  
  def liesIn(id:Int,start:Int,end:Int):Boolean=
  {
    if (start < end)
    {
      if (id > start && id <= end)
        return true
      else return false
    }
    else if (start>end)
    {
      return (!liesIn(id,end,start)) 
    }
    else // if start==end that means the entire chord circle is covered.
      return true
  }
  
  def liesBetween(id:Int,start:Int,end:Int):Boolean=
  {
    if (start < end)
    {
      if (id > start && id < end)
        return true
      else return false
    }
    else if (start>end)
    {
      if (!liesBetween(id,end,start) && (id != start) && (id!=end))
        return true
      else
        return false
    }
    else if ((id != start) && (id != end)) // if start==end that means the entire chord circle is covered.
      return true
    else 
      return false
  }
  
  def closest_preceding_finger(id:Int):ActorRef=
  {
    for (i<-m to 1 by -1)
    {
      if (liesBetween(finger_table(i-1).uid,UID,id))
        return finger_table(i-1).ref
    }
    // if no matching entry in finger table return null
    null
  }
  
  def receive = 
  {
    case `pp`=>
      {
        println("PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP")
      }
    case pass(ref:ActorRef) =>
      {
         println(UID.toString + "received pass from manager")
      }
    case join(ref:ActorRef)=>
      {
        Manager = sender
        if (ref.path.name == self.path.name)
        {
          //println(UID.toString + "received join from manager")
          //println ("************************************************************")
          predecessor = null
          successor = self
          // If I am here than I am the first node in the network.
          for (i<-1 to m)
          {
            finger_table(i-1).uid = UID
            finger_table(i-1).ref = self
          }
          // Send join ack to Manager
          Manager ! ack()
          val a_sys =  context.system
          import a_sys.dispatcher
          a_sys.scheduler.schedule(new FiniteDuration(10,MILLISECONDS),new FiniteDuration(30,MILLISECONDS),self,stabilize())
          a_sys.scheduler.schedule(new FiniteDuration(40,MILLISECONDS),new FiniteDuration(100,MILLISECONDS),self,fix_fingers())
        }
        else // I am joining the network with help from some other node
        {
          //println(UID.toString + "received join from manager")
          predecessor = null
          // ask helping node for my successor
          ref ! find_successor(UID,"join",self)
        }
      }
    case find_successor(id:Int,reason:String,origin_ref:ActorRef)=>
      {
        // Check if I can answer this query,send response directly to originator-
        var succ_uid = successor.path.name.toInt
        //println("**** FIND SUCCESSOR *****"+ origin_ref.path.name +" asks"+ " in " + UID.toString + " for "+ id.toString)
        if (liesIn(id,UID,succ_uid))
          {
            //print ("#####################################################")
            if (successor == null) // this is to remove issues in null typecasting to ActorRef
              {
                val return_successor = self
                val valid = false
                origin_ref ! your_successor(return_successor,reason,valid)
              }
            else
            {
               val return_successor = successor
               val valid = true
               origin_ref ! your_successor(return_successor,reason,valid)
            }
            
          }
        else // check local finger table and forward query to closer to someone who can answer it
        {
          //print ("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")
          var nextHop = closest_preceding_finger(id)
          if (nextHop != null) // forward query to it
            nextHop ! find_successor(id,reason,origin_ref)
          else // just forward query to your successor
            successor ! find_successor(id,reason,origin_ref)
        }
        
      }
    case your_successor(succ:ActorRef,reason:String,valid:Boolean)=>
      {
        if (reason == "join")// I received my successor from helping node.
          {
            //println (sender.path.name + " tells " + UID.toString + " its succ "+succ.path.name)
            if (valid)
              {
                successor = succ
                //println(self.path.name + " set succ to " + succ.path.name)
              }
            /* Now I can start the process of populating my finger table and
             * informing others of my presence
             * also I should inform the NetworkManager that he can proceed
             * with adding other nodes*/
            Manager ! ack()
            val a_sys =  context.system
            import a_sys.dispatcher
            a_sys.scheduler.schedule(new FiniteDuration(10,MILLISECONDS),new FiniteDuration(30,MILLISECONDS),self,stabilize())
            a_sys.scheduler.schedule(new FiniteDuration(40,MILLISECONDS),new FiniteDuration(100,MILLISECONDS),self,fix_fingers())
            
          }
        else // Must be a request I sent to get succesor for updating my fingers
        {
          val index = (reason.split("_")(2)).toInt
          if (valid)
          {
            //println(self.path.name + " received finger response for "+ finger_table(index).start.toString + " from "+sender.path.name + " response "+succ.path.name)
            finger_table(index).ref = succ
            finger_table(index).uid = succ.path.name.toInt
          }
        }
      }
    case stabilize()=>
      {
        //println("STABILIZE STABILIZE STABILIZE STABILIZE STABILIZE STABILIZE STABILIZE")
        // Ask successor for its predecessor to see if anything has changed
        successor ! what_is_your_predecessor()
      }
    case what_is_your_predecessor()=>
      {
        //println(sender.path.name + "asks for predecessor to "+ self.path.name)
        if (predecessor == null)
        {
          val pred = self
          val valid = false
          sender ! return_predecessor(pred,valid)
        }
        else
        {
          val pred = predecessor
          val valid = true
          sender ! return_predecessor(pred,valid)
        }
        //sender ! pp
      }
      // my successor returned me his predecessor--I have to check if there is someone 
      // between me and him
    case return_predecessor(pred:ActorRef,valid:Boolean)=>
      {
        
        if ((valid == true))
        {
          //println(sender.path.name + " return it's pred " + pred.path.name + " to "+ self.path.name)
          val pred_id = pred.path.name.toInt
          val succ_id = successor.path.name.toInt
          if (liesBetween(pred_id,UID,succ_id))
            {
              successor = pred
              //println(self.path.name + " set succ to "+ pred.path.name)
            }
        }
        // notify my successor of my existence
        successor ! chord_notify(self)
      }
    case chord_notify(ref:ActorRef)=>
      {
        //println(sender.path.name + " notifies itself to "+ self.path.name)
        // if I had no predecessor I will take this node as mine
        if (predecessor == null)
          {
            predecessor = ref
            //println(self.path.name + "set pred to "+ ref.path.name)
          }
        else 
        {
          val pred_id = predecessor.path.name.toInt
          val ref_id = ref.path.name.toInt
          // if this node lies between my pred and me--this is my new pred
          if (liesBetween(ref_id,pred_id,UID))
            { 
              predecessor = ref
              //println(self.path.name + "set pred to "+ ref.path.name)
            }
        }
      }
    case fix_fingers()=>
      {
        val range = 1 to m
        val index = scala.util.Random.nextInt(range.length)
        self ! find_successor(finger_table(index).start,"finger_update_"+index.toString(),self) 
        //println(self.path.name +"called fix_fingers on value "+finger_table(index).start.toString)
      }
    case `print_fingerTable`=>
      {
        var pred_name:String = null
        if (predecessor != null)
          pred_name = predecessor.path.name
          
        println("*******FT******** "+UID.toString+" *************")
        println ("PRED "+pred_name +" SUCC "+successor.path.name)
        //println("FT "+self.path.name+" "+finger_table(0).uid.toString+" "+finger_table(1).uid.toString+" "+finger_table(2).uid.toString+" "+finger_table(3).uid.toString)
        sender ! ack_print
      }
    case start_messaging()=>
      {
        /*compose a random message*/
        val msgID = utils.getUID(50,m)
        val initialHop = 0
        val a_sys =  context.system
        import a_sys.dispatcher
        for (i <- 1 to 10)
        {
          a_sys.scheduler.scheduleOnce(new FiniteDuration(i,SECONDS),self,msg(msgID,initialHop,self))
        }        
      }
    case msg(msgID:Int,hop_count:Int,origin_ref:ActorRef)=>
      {
        val h_count = hop_count + 1
        val limit = scala.math.pow(2,m).toInt
        if (h_count == limit)
          {
            Manager ! msg_ack(h_count)
            println("DROPPED")
          }
        else
        {
             // Check if I can answer this query,send response directly to originator-
          var succ_uid = successor.path.name.toInt
          println("**** Routing MSG to ***** "+ msgID.toString + " from "+ origin_ref.path.name + " at "+self.path.name)
          if (liesIn(msgID,UID,succ_uid)) // this node can answer this message
            {
               Manager ! msg_ack(h_count)
            }
          else // check local finger table and forward msg to closer to someone who can answer it
          {
            var nextHop = closest_preceding_finger(msgID)
            if (nextHop != null) // forward query to it
              nextHop ! msg(msgID,h_count,origin_ref)
            else // just forward msg to your successor
              successor ! msg(msgID,h_count,origin_ref)
          }
        }
      }
      
  }
}


