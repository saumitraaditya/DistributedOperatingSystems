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
case object Stop
//clients message
case class atYourService(numWorkers:Int)
case object gimmeMoreWork

//listener class shuts down sytstem
class sys_owner extends Actor{
  def receive={
    case Stop=>{
      context.system.shutdown()
    }
  }
}

// Supervisor actor
class Master(Listener:ActorRef,numCoins:Int,numZeros:Int) extends Actor{
  
   // Find out how many cores on the system
   val num_workers: Int = Runtime.getRuntime().availableProcessors()
   println("number of processors "+ num_workers)
   // keep track of workers
   val ListWorkers: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
   // keep track of remote machines
   val ListRemoteSupervisors: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
   var Start_Seed:Int = 0
   var remote_Seed:Int = num_workers * numCoins
   def receive = {
    case start_mining(start_seed:Int,num_zeroes:Int,numCoins:Int)=>
       //println("supervisor recvd signal to start")
       for (i <- 0 until num_workers){
         val worker = context.actorOf(Props[worker], "worker_" + i)
         ListWorkers+=worker
         worker ! work_chunk(Start_Seed,numCoins,num_zeroes)
         Start_Seed+=numCoins
       }
     case result_chunk(key:String,hash:String)=>{
       println(key+"\t"+hash)
       
     }
     case atYourService(numWorkers:Int)=>{
       println("Established Contact --Remote supervisor with "+ numWorkers + " workers.")
       sender ! start_mining(remote_Seed,numZeros,numCoins)
       remote_Seed+=(numCoins*numWorkers)
       //save reference to remote machine
       ListRemoteSupervisors.append(sender)
     }
     case `gimmeMoreWork`=>{
       remote_Seed+=numCoins
       sender ! work_chunk(remote_Seed,numCoins,numZeros)
     }
     case Stop=>{
       // signal all local workers to stop
       for (w <- ListWorkers){
         w ! PoisonPill
         }
       // signal remote supervisors to stop
       for (s <-ListRemoteSupervisors){
         s ! Stop
       }
         println("Master STOPPED")
         Listener ! Stop
     }
       
    
  }
   
}

// worker actor
class worker extends Actor{
  val ufid:String = "saumitraaditya"
  
  def receive ={
    case work_chunk(seed:Int,numCoins:Int,numZeroes:Int)=>{
      //println("worker recd signal to start")
      for (i <- seed until seed+numCoins){
        val inputString = ufid + RandomString(i)
        var hexString = DigestUtils.sha256Hex(inputString);
        //println(inputString+"::"+hexString)
        try{
          val cmp_val = Integer.parseInt(hexString.substring(0, numZeroes));
          if (cmp_val == 0)
          {
            sender ! result_chunk(inputString,hexString)  
            //println(inputString+"\t"+hexString)
          }
        }
        catch{
          case e:NumberFormatException => ;
        }
         sender ! gimmeMoreWork 
      }
      
    }
    
     
    
  }
  
  def RandomString(seed:Int):String={
    val R_string:String = Integer.toString(seed, 36)+scala.util.Random.alphanumeric.take(12).mkString
    R_string
  }

}


object BCMinerServer {
  def main(args:Array[String]){
  //Actor system
  if (args.length<1){
    println("Enter the desired minimum number of Zeros")
    return
  }
  val num_zeros = args(0).toInt
  val num_coins = 10000
  val actor_system = ActorSystem("RichMiners")
  val Listener = actor_system.actorOf(Props[sys_owner],"Listener")
  val Master = actor_system.actorOf(Props(new Master(Listener,num_coins,num_zeros)),"Master")
  import actor_system.dispatcher;
  actor_system.scheduler.scheduleOnce(200000 milliseconds, Master, Stop)
  // Hard Stop
  actor_system.scheduler.scheduleOnce(250000 milliseconds, Listener, Stop)
  Master ! start_mining(100,num_zeros,num_coins)
  }

  
  
}


