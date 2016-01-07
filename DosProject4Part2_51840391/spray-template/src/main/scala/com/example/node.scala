package com.example
import akka.actor.{ActorSystem, ActorRef, Actor, Props,actorRef2Scala,PoisonPill,ActorLogging}
import scala.collection.mutable.ArrayBuffer
import collection.mutable.HashMap
import spray.routing.RequestContext
import spray.httpx.SprayJsonSupport._
import spray.http.MediaTypes._
import spray.http._
import MediaTypes._
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
case class getSecureWall(uid:Int)
case class requestStatus(status:String)
case class returnWall(status:String,wall:Array[WallPost])
case class registerUser(uid:Int,name:String)
case class configure(graphMap:ArrayBuffer[ActorRef])
case class forgeFriendship(initiator_uid:Int,target_uid:Int) // Goes from client to FrontEnd
case class sealFriendShip(target_uid:Int,initiator_uid:Int) // goes from node A to B
case class getFriends(uid:Int)
case class getSecureFriends(frnd_list_req:secureFriendListRequest)
case class returnFriendList(friendList:Array[Int])
case class returnSecureFriendList(friendList:Array[(Int,String)])
case class WallPost(msg_type:String,submitterUID:Int,first_receiver_uid:Int,content:String,timeStamp:String)
case class secureWallPost(enc_msg:String,submitterUID:Int,timestamp:String,iv:String,enc_key:String)
case class secureWriteonWall(target_uid:Int,securepost:secureWallPost)// inter backend actor msg
case class getProfile(uid:Int)
case class retProfile(self_profile:myProfile)
case class createPage(name:String,topic:String,pid:Int,uid:Int)
case class getPage(pid:Int)
case class secure_page_post(submitter_uid:Int, timestamp:String, enc_post:String, iv:String, key_Array:Array[(Int,String)])
case class page_post(submitter_uid:Int,post:String,timestamp:String)
case class ret_page(name:String,topic:String,creator_uid:String,created:String,contents:Array[page_post])
case class postToSecurePage(pid:Int,uid:Int,post:secure_page_post,digiSign:String)
case class postToPage(pid:Int,uid:Int,post:String)
case class beMyFriend(initiator_uid:Int,target_uid:Int,publicKey:String)
case class ack_beMyFriend(initiator_uid:Int,target_uid:Int,publicKey:String)


/* Security related*/
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security._;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;


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
  /* Store public keys for the users I am responsible for upon registration*/
  val myPubKeys = new HashMap[Int,String]() { override def default(key:Int) = null }
   /* Store public keys for the subscribers of the page I am responsible for upon subscription request*/
  val myPagePubKeys = new HashMap[Int,String]() { override def default(key:Int) = null }
  /*
   * Messages to be handled by a node
   * putPosts
   * getWall
   * register
   * getProfile
   * getFriends
   * */
  def verifyDigitalSignature(uid:Int,msg:String,digiSig:String):Boolean=
  {
    try
    {
        Security.addProvider(new com.sun.crypto.provider.SunJCE());
      val fact = KeyFactory.getInstance("RSA");
      val cipher = Cipher.getInstance("RSA");
      val puKeyBytes =  new BASE64Decoder().decodeBuffer(myPubKeys(uid))
      val puspec = new X509EncodedKeySpec(puKeyBytes);
      val puKey = fact.generatePublic(puspec);
      cipher.init(Cipher.DECRYPT_MODE, puKey);
      val byteDecipherText = new BASE64Decoder().decodeBuffer(digiSig);
      val byteDecryptedText = cipher.doFinal(byteDecipherText);
      val strDecryptedText = new String(byteDecryptedText);
      println("input text "+digiSig)
      println("decrypted text  "+strDecryptedText)
      
      if (strDecryptedText == msg)
      {
        return true
      }
      else
      {
        return false
      }
    }
    catch 
    {
       case e: Exception => println("exception caught: " + e);
       println("Error verifying Digital signature")
       return false
    }
    
  }
  
  /*takes pub key as an argument, for verifying request for pages and direct write on Walls*/
  def verifyDigitalSignature(pubKey:String,msg:String,digiSig:String):Boolean=
  {
    try{
        Security.addProvider(new com.sun.crypto.provider.SunJCE());
        val fact = KeyFactory.getInstance("RSA");
        val cipher = Cipher.getInstance("RSA");
        val puKeyBytes =  new BASE64Decoder().decodeBuffer(pubKey)
        val puspec = new X509EncodedKeySpec(puKeyBytes);
        val puKey = fact.generatePublic(puspec);
        cipher.init(Cipher.DECRYPT_MODE, puKey);
        val byteDecipherText = new BASE64Decoder().decodeBuffer(digiSig);
        val byteDecryptedText = cipher.doFinal(byteDecipherText);
        val strDecryptedText = new String(byteDecryptedText);
        println("input text "+digiSig)
        println("decrypted text  "+strDecryptedText)
        
        if (strDecryptedText == msg)
        {
          return true
        }
        else
        {
          return false
        }
      }
    catch
    {
      case e: Exception => println("exception caught: " + e);
       println("Error verifying Digital signature")
       return false
    }
    
  }
  def receive=
  {
    case configure(graphMap:ArrayBuffer[ActorRef])=>
    {
      graph=graphMap
      println("configured----"+self.path.name)
    }
    case registerMsgWrapper(msg:registerMsg,requestContext:RequestContext)=>
      {
        val uid = msg.uid
        val publicKey = msg.pubKey
        val name = msg.name
        if (myMembers(uid)==null)
        {
          myMembers+=(uid->new user(name,uid))
          myPubKeys+=(uid->publicKey)
          println(uid)
          println(publicKey)
          println(name)
          myMembers(uid).setProfile(myProfile(uid,name,"Male","USA",20,publicKey))
          //HttpResponse(entity=HttpEntity(`text/plain`, "UnAuthorizedRequest"))
          requestContext.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Successfully registered!!")))
        }
        else
        {
          requestContext.complete(HttpResponse(entity=HttpEntity(`text/plain`, "UID exists try again with a different UID!")))
        }
      }
    /*case registerUser(uid:Int,name:String)=>
      {
        myMembers+=(uid->new user(name,uid))
        myMembers(uid).setProfile(myProfile(uid,name,"Male","USA",20))
        println("registerUser----"+self.path.name)
        sender ! requestStatus("ok")
      }*/
      
    case friendRequestWrapper(frnd_req:friendRequest,context:RequestContext)=>
      {
        if (myMembers(frnd_req.initiator_uid)!=null)
        {
           val target_uid = frnd_req.target_uid
           val hash_targetUID = org.apache.commons.codec.digest.DigestUtils.sha256Hex(target_uid.toString())
          /* verify digital signature before processing*/
          if (verifyDigitalSignature(frnd_req.initiator_uid,hash_targetUID,frnd_req.digital_signature))
          {
            /*send my information public key to target*/
           
            val publicKey = myPubKeys(frnd_req.initiator_uid)
            graph(target_uid%graph.length) ! beMyFriend(frnd_req.initiator_uid,frnd_req.target_uid,publicKey)
            
          }  
          else
          {
            context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Request not authorized, digital verification failed.")))
            println("Request not authorized, digital verification failed.")
          }
          /* upon ack from target add him to friendList and save his publicKey*/
        }
      }
    case beMyFriend(initiator_uid:Int,target_uid:Int,publicKey:String)=>
      {
         if (myMembers(target_uid)!=null)
         {
            myMembers(target_uid).secure_addFriend(initiator_uid,publicKey)
            val myPublicKey = myPubKeys(target_uid)
            graph(initiator_uid%graph.length) ! ack_beMyFriend(target_uid,initiator_uid,myPublicKey)
            println("-1--FriendShip Forged---"+target_uid.toString()+"->"+initiator_uid.toString())
            
         }
      }
    case ack_beMyFriend(initiator_uid:Int,target_uid:Int,publicKey:String)=>
      {
        //No meed to check uid , because I sent the originating request.
        myMembers(target_uid).secure_addFriend(initiator_uid,publicKey)
        println("-2--FriendShip Forged---"+target_uid.toString()+"->"+initiator_uid.toString())
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
    case SecureStatusUpdateWrapper(update:SecureStatusUpdate,context:RequestContext)=>
      {
        if (myMembers(update.myUID)!=null)
        {
          //Verify digital signature
          val hashMsg = org.apache.commons.codec.digest.DigestUtils.sha256Hex(update.payload)
           if (verifyDigitalSignature(update.myUID,hashMsg,update.digiSign))
           {
            println("digital signature verified for status update request.") 
            //secureWallPost(enc_msg:String,submitterUID:Int,timestamp:String,iv:String,enc_key:String)
            val enc_msg = update.payload
            val submitterUID = update.myUID
            val timestamp = update.timestamp
            val iv = update.iv
            var enc_key = update.keys(update.keys.length-1)._2// Last entry corresponds to (myUID,mykey)
            //post on myWall
            myMembers(update.myUID).securepostWall(secureWallPost(enc_msg,submitterUID,timestamp,iv,enc_key))
            // Now if have to route this post on to wall of my friends.
            for (i<- 0 until update.keys.length-1)
            {
              if(update.keys(i)._1>0)
              {
                enc_key = update.keys(i)._2
                val target_uid = update.keys(i)._1
                //secureWriteonWall(target_uid:Int,securepost:secureWallPost)
                graph(target_uid%graph.length) ! secureWriteonWall(target_uid,secureWallPost(enc_msg,submitterUID,timestamp,iv,enc_key))
              }
            }
            println("--------------SecureStatusUpdate Done---------------")
           }
           else
           {
             println("Unauthorized status update request.")
             context.complete(" Unauthorized request ")
           }
        }
        else
        {
          println(" User with this ID does not exists.")
          context.complete(" User with this ID does not exists.")
        }
      }
    case createSecurePageWrapper(pageCreationReq:createSecurePage,context:RequestContext)=>
      {
        val pid = pageCreationReq.pid
        val uid = pageCreationReq.creator_uid
        if (myPages(pid)==null && myMembers(uid)!= null)
        {
          if (verifyDigitalSignature(uid,pid.toString,pageCreationReq.digiSign))
          {
            myPages+=(pid->new page(pageCreationReq.name,pageCreationReq.topic,pageCreationReq.pid,pageCreationReq.creator_uid))
            myPages(pid).addSubscriber(uid, myPubKeys(uid))
            println("created new page----"+self.path.name)
            val ret = "Created a Page for "+uid.toString+ " with page ID "+pid.toString
            context.complete(HttpResponse(entity=HttpEntity(`text/plain`, ret)))
          }
          else
          { 
            context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "UnAuthorizedRequest")))
          }
        }
        else
        {
          context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Page with this PID exists try with another PID or Your UID is not correct.")))
        }
      }
    case subscribeToPageWrapper(subscription_req:subscribeToPage,context:RequestContext)=>
      {
        val pid = subscription_req.page_Id
        val owner_uid = subscription_req.owner_uid
        val requestor_uid = subscription_req.requestor_uid
        val requestor_pubKey = subscription_req.requestor_pubKey
        if ( myPages(pid) != null && myMembers(owner_uid)!= null)
        {
          myPages(pid).addSubscriber(requestor_uid, requestor_pubKey)
          context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Subscriber Added "+requestor_uid.toString)))
        }
        else
        {
          context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Please check details in subscription Request.")))
        }
      }
    case getSubscriptionListWrapper(subscriptionList_req:getSubscriptionList)=>
      {
         val pid = subscriptionList_req.page_id
         val requestor_uid = subscriptionList_req.requestor_uid
         val owner_uid = subscriptionList_req.owner_uid
         val digiSign = subscriptionList_req.digiSign
         var flag = false /*requestor must be a subscriber to get List*/
         var requestor_pubKey = ""
         if ( myPages(pid) != null && myMembers(owner_uid)!= null)
         {
           for (i<- 0 until myPages(pid).SubscriberPubKeys.length)
           {
             if (myPages(pid).SubscriberPubKeys(i)._1==requestor_uid)
             {
               flag = true
               requestor_pubKey = myPages(pid).SubscriberPubKeys(i)._2
             }
           }
           if (flag == true && verifyDigitalSignature(requestor_pubKey,pid.toString,digiSign))
          {  
             println ("----------------RETURN SUBSC LIST ----------------------")
             sender !  retSubscriptionList(myPages(pid).SubscriberPubKeys.filter(_._1>0))  
          }
           else
           {
             sender ! retSubscriptionList(Array((-99,""))) // have to do this because client expects Array object when parsed.
           }
         }
         else
         {
           sender ! retSubscriptionList(Array((-99,"")))
         }
      }
    case securePostToPageWrapper(post:securePostToPage,requestContext:RequestContext)=>
      {
        val owner_uid = post.owner_id
        val submitter_uid = post.page_post.submitter_uid
        val digiSign = post.digiSign
        val page_id = post.page_id
        var flag = false /*requestor must be a subscriber to get List*/
        var submitter_pubKey = ""
        val hashPost = org.apache.commons.codec.digest.DigestUtils.sha256Hex(post.page_post.enc_post)
        if (myMembers(owner_uid)!=null && myPages(page_id)!=null)
        {
          for (i<- 0 until myPages(page_id).SubscriberPubKeys.length)
           {
             if (myPages(page_id).SubscriberPubKeys(i)._1==submitter_uid)
             {
               flag = true
               submitter_pubKey = myPages(page_id).SubscriberPubKeys(i)._2
             }
           }
           if (flag == true && verifyDigitalSignature(submitter_pubKey,hashPost,digiSign))
          {            
               myPages(page_id).securePost(post.page_post)
               requestContext.complete(HttpResponse(entity=HttpEntity(`text/plain`, "post submitted to page")))
          }
           else
           {
             requestContext.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Unauthorized post attempt to page")))
           }
         }
         else
         {
           requestContext.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Please check the pageID, ownerID for the page")))
         }
          
        }
    case getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)=>
      {
        /* No sign verification here all content is encrypted so the receiver can't do anything with it.*/
        if (myMembers(ownerID)!=null && myPages(pageID)!=null)
        {
          sender ! ret_secure_page(myPages(pageID).page_name,myPages(pageID).about,myPages(pageID).creator_id,myPages(pageID).created,myPages(pageID).secureContents.filter(_.submitter_uid>0))
        }
        else
        {
          sender ! ret_secure_page("","",-99,"",myPages(pageID).secureContents)
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
    case secureWriteonWall(target_uid:Int,securepost:secureWallPost)=>
      { /*This is a post on my Wall by my Friend*/
        if (myMembers(target_uid)!=null && myMembers(target_uid).isFriend(securepost.submitterUID)==true)
        {
           myMembers(target_uid).securepostWall(securepost)
        }
      }
     /* Below is when you only want to write on a Friends Wall and not do a status update which is a
      * broadcast to all your friends*/
    case SecureWriteOnWallWrapper(post:SecureWriteOnWall,context:RequestContext)=>
      {
        val target_uid = post.targetUID
        val submitter_uid = post.myUID
        val hashPost = org.apache.commons.codec.digest.DigestUtils.sha256Hex(post.payload)
        val digiSign = post.digiSign
        var submitterPubKey = ""
        
        if (myMembers(target_uid)!=null && myMembers(target_uid).isFriend(submitter_uid)==true)
        {
          for (i<- 0 until myMembers(target_uid).secure_friendList.length )
          {
            if (myMembers(target_uid).secure_friendList(i)._1 == submitter_uid)
            {
              submitterPubKey = myMembers(target_uid).secure_friendList(i)._2
            }
          }
          if (verifyDigitalSignature(submitterPubKey,hashPost,digiSign))
          {
            myMembers(target_uid).securepostWall(secureWallPost(post.payload,submitter_uid,post.timestamp,post.iv,post.encKey))
            context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "post written to Wall")))
          }
          else
          {
            context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Unauthorized attempt to write on Wall")))
          }
        }
       
        else
        {
          context.complete(HttpResponse(entity=HttpEntity(`text/plain`, "Check targetID, are you friends with him ?")))
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
    case getSecureWall(uid:Int)=>
      {
         if (myMembers(uid)!=null)
         {
           var ret_secureWall = myMembers(uid).secureWall.filter(_.submitterUID > 0)
           if (ret_secureWall.length == 0)
           {
             ret_secureWall = Array.fill[secureWallPost](1)(secureWallPost("",-99,"","",""))
           }
           sender ! secureWall(uid,ret_secureWall)
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
    case getSecureFriends(frnd_list_req:secureFriendListRequest)=>
      {
        val uid = frnd_list_req.uid
        /* Due to Asynchronous nature a friendList request can arrive even before friendship has
         * been established*/
        var securefriendList = myMembers(uid).secure_friendList.filter(_._1>0)
        if (securefriendList.length == 0)
        {
          sender ! returnSecureFriendList(Array((-99,"")))
        }
        else
          {
          sender ! returnSecureFriendList(securefriendList)
          }
      }
    case getFriends(uid:Int)=>
      {
        val friendList = myMembers(uid).friendList.filter(_ > 0)
        sender ! returnFriendList(friendList)
      }
    case myProfile(uid:Int,name:String,sex:String,location:String,age:Int,pubKey:String)=>
      {
        val profile = myProfile(uid,name,sex,location,age,pubKey)
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