import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit;


case class coordinate(x:Int,y:Int,z:Int)
case object start // main method sends to monitor
case object rumor // monitor sends to sensor, sensor sends to sensor
case object end // sensor sends to monitor
case class configure(network:ArrayBuffer[ActorRef],topology:String,algorithm:String)// Monitor sends to sensor
case class ps(sum:Double,weight:Double) // push sum messages exchanged between sensors
case object startps // message sent from monitor to sensor to start push-sum chain
case class endps(sum_estimate:Double) // message sent to monitor from sensor
case class chaos() //sent to monitor at fixed durations to introduce failures in the sensor networks.
case class remove_neighbour(id:Int) //sent from monitor to node to remove neighbour with ID = id
case class removed(id:Int) // sent from sensor to monitor as ack for neighbour removal

// Monitor keeps track of the status of sensors in the network
class sensor_status(num_neighbours:Int,alive:Boolean)
{
  var my_ne = num_neighbours
  var my_status = alive
}


// This object contains necessary utility functions for topology management
object topo_utilities
{
  def find_neighbours(id:Int,numNodes:Int,topology:String):ArrayBuffer[Int]=
  {
    var neighbours = ArrayBuffer[Int]()
    // if topology is a line we have only two neighbours - left & right
    if (topology == "line")
    {
      //handle special cases of first and last node
      if (id == 1)
        neighbours+=(id+1)
      else if (id == numNodes)
        neighbours+=(id-1)
      else
      {
        neighbours+=(id-1)
        neighbours+=(id+1)
      }
    }
    // if topology is full network, we have all to all connectivity
    else if (topology == "full")
    {
      for (i <- 1 to numNodes)
      {
        neighbours+=i
      }
      neighbours-=id
    }
    // if topology is 3d, location has 3 co-ordinates-x,y,z 
    else if (topology == "3D")
    {
      // first
      var x:Int=0
      var y:Int=0
      var z:Int=0
      // we want to approximate a cube structure-- think it of pile of squares stacked over each other
      // if the number of nodes enetered is not a perfect cube, the limits will be approximated to a Integer
      // closest to the cube root
      var x_limit= math.cbrt(numNodes).ceil.toInt
      var y_limit = x_limit
      var z_limit = (numNodes/(x_limit*y_limit)).ceil.toInt
      //get coordinates of this sensor
      val crd = id_to_coordinates(id,x_limit,y_limit)
      //get neighbours--should be 6, 2 on X-axis, 2 on Y-axis,2 on Z-axis--will handle corner cases
      val crd1 = coordinate(crd.x,crd.y,crd.z-1)
      if (check_coordinates(crd1,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd1,x_limit,y_limit,z_limit)
      val crd2 = coordinate(crd.x,crd.y,crd.z+1)
      if (check_coordinates(crd2,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd2,x_limit,y_limit,z_limit)
      val crd3 = coordinate(crd.x,crd.y-1,crd.z)
      if (check_coordinates(crd3,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd3,x_limit,y_limit,z_limit) 
      val crd4 = coordinate(crd.x,crd.y+1,crd.z)
      if (check_coordinates(crd4,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd4,x_limit,y_limit,z_limit)
      val crd5 = coordinate(crd.x-1,crd.y,crd.z)
      if (check_coordinates(crd5,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd5,x_limit,y_limit,z_limit)
      val crd6 = coordinate(crd.x+1,crd.y,crd.z)
      if (check_coordinates(crd6,x_limit,y_limit,z_limit,numNodes)==true)
        neighbours+=coordinates_to_id(crd6,x_limit,y_limit,z_limit)
    } 
    else if (topology == "imp3D")
    {
      // get grid neighbours
      neighbours = find_neighbours(id,numNodes,"3D")
      //get one random neighbour
      var random_neighbour:Int=0
      do
        {random_neighbour = scala.util.Random.nextInt(numNodes+1)}
      while(random_neighbour==id)
      if (random_neighbour == 0)
        random_neighbour = 1
      // add random_neighbour to list
      neighbours += random_neighbour
    }
    neighbours
  }
  
  // This method returns ID of a point given its coordinates
  def coordinates_to_id(crd:coordinate,x_lim:Int,y_lim:Int,z_lim:Int):Int=
  {
    val id = ((crd.z -1)*x_lim*y_lim) + (x_lim*(crd.y-1)) + crd.x
    id
  }
  
  //This method returns coordinates given ID
  def id_to_coordinates(id:Int,x_lim:Int,y_lim:Int):coordinate=
  {
    var rem = id.toDouble
    var z = (rem/(x_lim*y_lim)).ceil.toInt
    rem = rem - ((z-1)*x_lim*y_lim)
    var y = (rem/x_lim).ceil.toInt
    rem = rem - ((y-1)*x_lim)
    var x = rem.toInt
    return coordinate(x,y,z)
    
  }
  
  //This method checks to see if coordinates are valid for the node
  // handles nodes on end-edges and corners
  def check_coordinates(crd:coordinate,x_lim:Int,y_lim:Int,z_lim:Int,numNodes:Int):Boolean=
  {
    if (crd.x < 1 || crd.x > x_lim)
      return false
    if (crd.y < 1 || crd.y > y_lim)
      return false
    if (crd.z < 1 || crd.z > z_lim)
      return false
    // check if node with this ID exists
    if (coordinates_to_id(crd,x_lim,y_lim,z_lim) > numNodes)
      return false
    return true
  }
  
}

object project2_bonus {
  def main(args: Array[String]){
    if (args.length < 5)
    {
      println("Please enter <num of sensors> <topology> <algorithm> <initial_dealy> <interval>")
      return
    }
    else
    {
      val numNodes = args(0).toInt
      val topology = args(1)
      val algorithm = args(2)
      val initial_delay = args(3).toLong
      val interval = args(4).toLong
      // create collection of Actors
      val actor_system = ActorSystem("SensorSystem")
      var sensors = ArrayBuffer[ActorRef]()
      for (i <- 1 to numNodes)
      {
        // get a collection of neighbouring sensors for this sensor based on the topology
        var neighbours = topo_utilities.find_neighbours(i,numNodes,topology)
        println ("neighbours of "+i.toString + " length "+neighbours.length.toString)
        for (j <- neighbours)
        {
          print(j.toString + "\t")
        }
        println("")
        sensors += actor_system.actorOf(Props(new sensor(neighbours,i.toDouble)),"sensor"+i.toString)
      }
      // set up a Monitor actor to keep track of all the sensors
      val monitor = actor_system.actorOf(Props(new Monitor(sensors,topology,algorithm)),"monitor")
      // Monitor will send all required information to sensors to initialize them
      monitor ! start
      /* To simulate failures send a special message to monitor at fixed intervals 
       * to stop a sensor in the network*/
      import actor_system.dispatcher
      actor_system.scheduler.schedule(new FiniteDuration(initial_delay,MILLISECONDS) ,new FiniteDuration(interval,MILLISECONDS),monitor,chaos())
    }
    
  }
    
}

class Monitor(sensors:ArrayBuffer[ActorRef],topology:String,algorithm:String) extends Actor
{
  
  var start_time = 0.0
  var counter = 0 // keeps track of sensors who have finished
  var numNodes = sensors.length
  var status_log = new Array[sensor_status](numNodes)
  
  // initially all nodes are alive and num_neighbours is -1, i.e. not known
  for (i <- 0 to numNodes-1)
  {
    status_log(i)=new sensor_status(-1,true)
  }
  
  
  def receive =
  {
    case `start` =>
      {
        // initialize all the sensors
        for (sensor <- sensors)
        {
          sensor ! configure(sensors,topology,algorithm)
        }
        var random = scala.util.Random.nextInt(numNodes)
        if (algorithm == "gossip")
        {
          start_time = System.currentTimeMillis
          // choose a random sensor and send it the signal to start the rumor
          sensors(random) ! rumor
        }
        else if (algorithm == "push-sum")
        {
            start_time = System.currentTimeMillis
            sensors(random) ! startps
        }
        
      }
      
    case chaos()=>
      {
         /*Simulate failures in the network
         * 1. Identify a random sensor from the network.
         * 2. identify the neighbours of this sensor
         * 3. Inform the neighbours to remove this node from their neighbour_list
         * 4. wait for acknowledgement from the neighbour_nodes
         * 5. kill the node by sending it a poison pill
         */
        var random = -1
        // if node already marked for removal find another one.
        do
        {random = scala.util.Random.nextInt(numNodes)}
        while(status_log(random-1).my_status == false)
        if (random==0)
          random = 1
        val neighbours = topo_utilities.find_neighbours(random,numNodes,topology)
        status_log(random-1).my_ne = neighbours.length
        status_log(random-1).my_status = false // node marked for removal
        for (i <- neighbours)
        {
          sensors(i-1) ! remove_neighbour(random)
        }
        
      }
      
    case removed(id:Int)=>
      {
        // decrement neighbours
        status_log(id-1).my_ne-=1
        // when node has been removed from all neighbour lists kill it
        if (status_log(id-1).my_ne == 0)
        {
          sensors(id-1) ! PoisonPill
        }
      }
      
    case `end` =>
      {
          var end_time = System.currentTimeMillis
          println("The message has been consumed by the network, it's time to say GoodBye!!")
          println("Time taken for convergence is "+(end_time-start_time).toString)
          context.system.shutdown()
        /*counter+=1
        if (counter == numNodes)
        {
          var end_time = System.currentTimeMillis
          println("All sensors have completed their tasks, it's time to say GoodBye!!")
          println("Time taken for convergence is "+(end_time-start_time).toString)
          context.system.shutdown()
        }*/
      }
    case endps(sum_estimate:Double) =>
      {
          var end_time = System.currentTimeMillis
          println("The message has been consumed by the network, it's time to say GoodBye!!")
          println("sum_estimate is "+sum_estimate.toString)
          println("Time taken for convergence is "+(end_time-start_time).toString)   
          context.system.shutdown()
        /*counter+=1
        if (counter == numNodes)
        {
          var end_time = System.currentTimeMillis
          println("sum_estimate is "+sum_estimate.toString)
          println("All sensors have completed their tasks, it's time to say GoodBye!!")
          println("Time taken for convergence is "+(end_time-start_time).toString)
          context.system.shutdown()
        }*/
      }
      
  }
  
  
}

class sensor(myneighbours:ArrayBuffer[Int],sval:Double) extends Actor
{
  var neighbours = myneighbours
  var network:ArrayBuffer[ActorRef]=null
  var topology:String=""
  var algorithm:String=""
  var s:Double = sval
  var w:Double = 1.0
  var ratio:Double=0.0
  var counter:Int = 0
  var monitor:ActorRef=null
  var ratio_tracker = new Array[Double](2)
  ratio_tracker(0)= s/w
  var ratio_counter = 0
  var term_counter:Int=0
  
  def receive =
  {
    case configure(sensors:ArrayBuffer[ActorRef],topo:String,algo:String)=>
      {
        network = sensors
        topology = topo
        algorithm = algo
        monitor = sender
      }
    case `rumor`=>
      {
        counter += 1
        println("received rumor---- "+ self.path.name + " count "+counter.toString)
        if (counter==10)
        {
          println("I have finished my task---- "+ self.path.name)
          monitor ! end
          context.stop(self)
        }
        else
        {
          //select a random neighbour
          var random = scala.util.Random.nextInt((neighbours.length))
          // forward rumor to it
          println("neighnours-->\n")
          for (i <- neighbours)
          {
            print(i.toString+"\t")
          }
          println(self.path.name + " sends to " + network(neighbours(random)-1).path.name)
          network(neighbours(random)-1) ! rumor 
        }
      }
    case `startps`=>
      {
        var msg = ps(s/2,w/2)
        s = s/2
        w = w/2
        ratio_counter +=1
        ratio_tracker(ratio_counter%2)=s/w
        var random = scala.util.Random.nextInt((neighbours.length))
        // forward ps to it
        println("\n")
        for (i <- neighbours)
        {
          print(i.toString+"\t")
        }
        println(self.path.name + " sends to " + network(neighbours(random)-1).path.name)
        network(neighbours(random)-1) ! msg
        
      }
    case ps(sum:Double,weight:Double)=>
      {
        s+=sum
        w+=weight
        ratio_counter+=1
        ratio_tracker(ratio_counter%2)=s/w
        var curr_index = ratio_counter%2
        var prev_index = 0
        if (curr_index == 1)
          prev_index = 0
        else 
          prev_index = 1
        // check delta
        if ((ratio_tracker(curr_index)-ratio_tracker(prev_index)).abs < 1e-10)
            term_counter+=1
        else // reset term counter
            term_counter = 0
        if (term_counter == 3)
          {
          ///////////////////////////////////////////////////
            /*var msg = ps(s/2,w/2)
            s = s/2
            w = w/2
            ratio_counter +=1
            ratio_tracker(ratio_counter%2)=s/w
            var random = scala.util.Random.nextInt((neighbours.length))
            // forward ps to it
            println("\n")
            for (i <- neighbours)
            {
              print(i.toString+"\t")
            }
            println(self.path.name + " sends to " + network(neighbours(random)-1).path.name)
            network(neighbours(random)-1) ! msg*/
           //////////////////////////////////////////////////////// 
            println("I have finished my task---- "+ self.path.name)
            monitor ! endps(ratio_tracker(curr_index))
            context.stop(self)
          }
        else
        {
          var msg = ps(s/2,w/2)
          s = s/2
          w = w/2
          ratio_counter +=1
          ratio_tracker(ratio_counter%2)=s/w
          var random = scala.util.Random.nextInt((neighbours.length))
          // forward ps to it
          println("neighbours-->")
          println("\n")
          for (i <- neighbours)
          {
            print(i.toString+"\t")
          }
          println(self.path.name + " sends to " + network(neighbours(random)-1).path.name)
          network(neighbours(random)-1) ! msg
        }
       
        
      }
    case remove_neighbour(id:Int)=>
      {
        // remove the neighbour from the list
        neighbours -= id
        println(self.path.name + " removed node "+id.toString+" from my neighbour list "+" updated neighbours--")
        println("")
        for (i <- neighbours)
        {
          print(i.toString+ "\t")
        }
      }
  }
  
}




