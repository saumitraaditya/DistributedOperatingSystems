import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import scala.collection.mutable.ArrayBuffer
import java.security.MessageDigest
import org.apache.commons.codec.digest.DigestUtils;
import scala.concurrent.duration.DurationInt

// to ensure workers do not mine the same coins 
//use a seed value to generate random strings
case class work_chunk(seed:Int,numCoins:Int,numZeroes:Int)
// message representing portion of results from the
//workers
case class result_chunk(key:String,hash:String)
case class start_mining(start_seed:Int,num_zeroes:Int,numCoins:Int)
//Message to be sent to server
case class atYourService(numWorkers:Int)
case object Stop
case object gimmeMoreWork

// listener class shuts down system
  class sys_owner extends Actor{
    def receive ={
      case Stop=>{
        context.system.shutdown()
      }
      
    }
    
  }

// Supervisor actor
class supervisor(Listener:ActorRef,ServerIP:String) extends Actor{
  
   // Find out how many cores on the system
   val num_workers: Int = Runtime.getRuntime().availableProcessors()
   println("number of processors "+ num_workers)
   // keep track of workers
   val ListWorkers: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
   // keep track of Results
   val Results = new ArrayBuffer[String]
   // get the reference for remote actor
   val Master = context.actorFor("akka.tcp://RichMiners@" + ServerIP + "/user/Master")
   // send a message to master telling you are ready
   Master ! atYourService(num_workers)
   var Start_Seed:Int = 0
   def receive = {
    case start_mining(start_seed:Int,num_zeroes:Int,numCoins:Int)=>
       println("supervisor recvd signal to start")
       Start_Seed=start_seed
       for (i <- 0 until num_workers){
         val worker = context.actorOf(Props(new worker(Master)), "worker_" + i)
         ListWorkers+=worker
         worker ! work_chunk(Start_Seed,numCoins,num_zeroes)
         Start_Seed+=numCoins
       }
     case result_chunk(key:String,hash:String)=>{
       //println(key +"\t"+hash)
       
     }
     case Stop=>{
       // signal all workers to stop
       for (w <- ListWorkers){
         w ! PoisonPill
         }
         println("SUPERVISOR STOPPED")
         Listener ! Stop
     }
       
    
  }
   
}

// worker actor
class worker(Master:ActorRef) extends Actor{
  val ufid:String = "saumitraaditya"
  
  def receive ={
    case work_chunk(seed:Int,numCoins:Int,numZeroes:Int)=>{
      //println("worker recd signal to start")
      for (i <- seed until seed+numCoins){
        val inputString = ufid + "b" + RandomString(i)
        var hexString = DigestUtils.sha256Hex(inputString);
        try{
          val cmp_val = Integer.parseInt(hexString.substring(0, numZeroes));
          if (cmp_val == 0)
          {
            Master ! result_chunk(inputString,hexString)  
            println(inputString+"\t"+hexString)
          }
        }
        catch{
          case e:NumberFormatException => ;
        }
         Master ! gimmeMoreWork 
      }
      
    }
     
    
  }
  
  
  def RandomString(seed:Int):String={
    val R_string:String = Integer.toString(seed, 36)+scala.util.Random.alphanumeric.take(12).mkString
    R_string
  }

}


object BCMinerClient {
  def main(args: Array[String]){
  if (args.length<1)
  {
    println("Please enter Server IP")
    return
  }
  val ServerIP = args(0)
  //Actor system
  val actor_system = ActorSystem("RichMiners")
  val Listener = actor_system.actorOf(Props[sys_owner],"Listener")
  val supervisor = actor_system.actorOf(Props(new supervisor(Listener,ServerIP)),"supervisor")
  }

  
  
}



