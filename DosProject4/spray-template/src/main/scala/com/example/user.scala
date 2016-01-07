package com.example
import scala.collection.mutable.ArrayBuffer
import java.util.Date
import java.util.Calendar
import java.text._
import collection.mutable.HashMap
/*
 * This data-structure maintains the state of a user-
 * i.e. fields required for his profile,
 * his wall/page which is essentially a collection of
 * posts made by him or his friends
 * and his friend list
 */
class user(name:String,uid:Int)
{
  /*
   * can add other details like
   * location, DoB , sex
   * */
  var profile:myProfile = myProfile(-99," "," "," ",0)
  var wallIndex:Int = 0
  var friendIndex:Int = 0 
  val isFriend = new HashMap[Int,Boolean]() { override def default(key:Int) = false }
  /* Have to impose limitation due to 
   * memory constraints*/
  val storeSize = 100
  val MaxFriends = 1000
  //var m = Array.fill[Int](storeSize)(-99)
  var wall = Array.fill[WallPost](storeSize)(WallPost("invalid",-9999,-9999,"InValid","InValid")) // Blank spaces indicate unoccupied slot
  var friendList = Array.fill[Int](MaxFriends)(-99) // Negative value indicates free slot
  /* Initialize the arrays */
  
  def postWall(msg_class:String, sender_uid:Int, first_receiver_uid:Int,post:String)
  {
    val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
    val timestamp = new Date()
    val mypost = WallPost(msg_class,sender_uid,first_receiver_uid,post,timestamp.toString)
    if (wallIndex >= storeSize)
    {
      wallIndex = 0
    }
    wall(wallIndex) = mypost
    wallIndex+=1
    
  }

  def addFriend(uid:Int):Unit =
  {
    if (friendIndex >= MaxFriends)
    {
      return
    }
    else
    {
      isFriend+=(uid->true)
      friendList(friendIndex)=uid
      friendIndex+=1
    }
  }
  def setProfile(self_profile:myProfile)
  {
    profile = self_profile
  }
  def getProfile():myProfile=
  {
    profile
  }
  
}


class page(name:String,topic:String,pid:Int,uid:Int)
{
  val page_name:String = name
  val MaxPosts = 1000
  val page_id:Int = pid
  val creator_id:Int = uid
  val about:String = " "
  val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
  val timestamp = new Date()
  val created = timestamp.toString
  var contents = Array.fill[page_post](MaxPosts)(page_post(-99," "," "))
  var post_index = 0
  
   def postPage(submitter_uid:Int,post:String)
  {
    val timestamp = new Date()
    val mypost = page_post(submitter_uid,post,timestamp.toString)
    if (post_index >= MaxPosts)
    {
      post_index = 0
    }
    contents(post_index) = mypost
    post_index+=1
    
  }
  
  
}