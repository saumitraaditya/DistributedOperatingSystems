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
case class result_chunk(part_result:String)
case class start_mining(start_seed:Int,num_zeroes:Int,numCoins:Int)
case class Stop()
case class gimmeMoreWork()

// listener class shuts down system
  class sys_owner extends Actor{
    def receive ={
      case Stop()=>{
        context.system.shutdown()
      }
      
    }
    
  }

// Supervisor actor
class supervisor(Listener:ActorRef,numCoins:Int,num_zeroes:Int) extends Actor{
  
   // Find out how many cores on the system
   val num_workers: Int = Runtime.getRuntime().availableProcessors()
   println("number of processors "+ num_workers)
   // keep track of workers
   val ListWorkers: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
   // keep track of Results
   val Results = new ArrayBuffer[String]
   var Start_Seed:Int = 0
   def receive = {
    case start_mining(start_seed:Int,num_zeroes:Int,numCoins:Int)=>
       println("supervisor recvd signal to start")
       for (i <- 0 until num_workers){
         val worker = context.actorOf(Props[worker], "worker_" + i)
         ListWorkers+=worker
         worker ! work_chunk(Start_Seed,numCoins,num_zeroes)
         Start_Seed+=numCoins
       }
       
     case gimmeMoreWork()=>
       //println("More Work!!!")
       Start_Seed+=numCoins
       sender ! work_chunk(Start_Seed,numCoins,num_zeroes)
     
     
     case result_chunk(part_result:String)=>
       Results.append(part_result)
       
     
     case Stop()=>
       // signal all workers to stop
       for (w <- ListWorkers){
         w ! PoisonPill
         }
       /*for (myString<-Results)
         {
           println(myString)
         }*/
         println("SUPERVISOR STOPPED")
         Listener ! Stop()
     
       
    
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
        var hexString = org.apache.commons.codec.digest.DigestUtils.sha256Hex(inputString);
        //println(inputString+"::"+hexString)
        try{
          val cmp_val = Integer.parseInt(hexString.substring(0, numZeroes));
          if (cmp_val == 0)
          {
            sender ! result_chunk(inputString+":"+hexString)  
            println(inputString+"\t"+hexString)
          }
        }
        
        catch{
          case e:NumberFormatException => ;
        }
          
      }
      sender ! gimmeMoreWork
      
    }
     
    
  }
  
 
  
  def RandomString(seed:Int):String={
    val R_string:String = Integer.toString(seed, 36)+scala.util.Random.alphanumeric.take(12).mkString
    R_string
  }

}


object project1 {
  def main(args: Array[String]){
    if (args.length<1)
    {
      println("Please enter desired minimum number of Zeros")
      return
    }
  //Actor system
  val numCoins = 100000
  val numZeros = args(0).toInt
  val actor_system = ActorSystem("RichMiners")
  val Listener = actor_system.actorOf(Props[sys_owner],"Listener")
  val supervisor = actor_system.actorOf(Props(new supervisor(Listener,numCoins,numZeros)),"supervisor")
  import actor_system.dispatcher;
  actor_system.scheduler.scheduleOnce(300000 milliseconds, supervisor, Stop())
  // Hard Stop--If stop messages get buried, with a grace of 50000 ms
  actor_system.scheduler.scheduleOnce(350000 milliseconds, Listener, Stop())
  supervisor ! start_mining(100,4,100000)
  }
  
}


