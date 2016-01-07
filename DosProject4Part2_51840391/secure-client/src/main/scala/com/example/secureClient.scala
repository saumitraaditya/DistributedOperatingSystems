package com.example
/* Spray and generic */
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
import scala.concurrent.Future
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
import javax.crypto.spec.SecretKeySpec;
import java.security._;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.MessageDigest
import org.apache.commons.codec.digest.DigestUtils;


case class registerMsg(name:String,uid:Int,pubKey:String)
object registrationProtocol extends DefaultJsonProtocol {
  implicit val registrationFormat = jsonFormat3(registerMsg) 
}

case class friendRequest(initiator_uid:Int, target_uid:Int,digital_signature:String)
object friendRequestProtocol extends DefaultJsonProtocol{
  implicit val friendReq = jsonFormat3(friendRequest)
}

case class returnSecureFriendList(friendList:Array[(Int,String)])
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

case class getSecureWall(uid:Int)
case class secureWallPost(enc_msg:String,submitterUID:Int,timestamp:String,iv:String,enc_key:String)
case class secureWall(uid:Int,items:Array[secureWallPost])
object secureWallProtocol extends DefaultJsonProtocol{
  implicit val secureWallPostFormat = jsonFormat5(secureWallPost)
  implicit val secureWallFormat = jsonFormat2(secureWall)
}

case class SecureWriteOnWall(targetUID:Int,myUID:Int,payload:String,timestamp:String,iv:String,encKey:String,digiSign:String)
object SecureWriteOnWallProtocol extends DefaultJsonProtocol{
  implicit val SecureWriteOnWallFormat = jsonFormat7(SecureWriteOnWall)
}
case class SecureWriteOnWallWrapper(post:SecureWriteOnWall,context:RequestContext)

case class createSecurePage(name:String,topic:String,pid:Int,creator_uid:Int,digiSign:String)
object createSecurePageProtocol extends DefaultJsonProtocol{
  implicit val createSecurePageFormat = jsonFormat5(createSecurePage)
}

case class subscribeToPage(requestor_uid:Int,owner_uid:Int,page_Id:Int,requestor_pubKey:String)
object subscribeToPageProtocol extends DefaultJsonProtocol{
  implicit val subscribeToPageFormat = jsonFormat4(subscribeToPage)
}

case class getSubscriptionList(requestor_uid:Int,owner_uid:Int,page_id:Int,digiSign:String)
object getSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val getSubscriptionListFormat = jsonFormat4(getSubscriptionList)
}

case class retSubscriptionList(subscribers:Array[(Int,String)])
object retSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val retSubscriptionListFormat = jsonFormat1(retSubscriptionList)
}

case class secure_page_post(submitter_uid:Int, timestamp:String, enc_post:String, iv:String, key_Array:Array[(Int,String)])
case class securePostToPage(page_post:secure_page_post,page_id:Int,owner_id:Int,digiSign:String)
object securePostToPageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val securePostToPageFormat = jsonFormat4(securePostToPage)
}

case class ret_secure_page(name:String,topic:String,creator_uid:Int,created:String,contents:Array[secure_page_post])
object ret_secure_pageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val ret_secure_pageFormat = jsonFormat5(ret_secure_page)
}

case class register(uid:Int)
case class frndReq(initiator:Int,target:Int)
case class getFrndList(uid:Int)
case class updateStatus(uid:Int,msg:String)
case class secureWriteOnWall(myUID:Int,targetUID:Int,msg:String)
case class createPage(uid:Int,pid:Int,name:String,topic:String)
case class pageSubscription(requestorID:Int,ownerID:Int,pageID:Int)
case class getSubscribers(requestorID:Int,ownerID:Int,pageID:Int)
case class postToPage(submitterId:Int,ownerID:Int,pageID:Int,msg:String)
case class getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)
case class getProfile(targetUID:Int)
case object testSignature


object utils{
val maxUsers = 99999
val base_uri = "http://localhost:8080/graph"
val register_uri = base_uri + "/registration" // 1
val updateStatus_uri = base_uri + "/secureStatusUpdate" // 2
val makeFriends_uri = base_uri + "/friendRequest" // 3
val makePost_uri = base_uri + "/sendPost" // 4
val friendList_uri = base_uri + "/SecureFriendList"// 5
val wall_uri = base_uri + "/showSecureWall"// 6
val setProfile_uri = base_uri +"/updateProfile" //7
val getProfile_uri = base_uri + "/getProfile" //8
val createPage_uri = base_uri +"/createSecurePage" //9
val pageSubscription_uri = base_uri + "/subscribePage" //10
val getSubscribers_uri = base_uri + "/subscriptionList" //11
val postToPage_uri = base_uri + "/securePagePost" //12
val getPage_uri = base_uri + "/showSecurepage" //13
val writeOnwall_uri = base_uri + "/secureWriteOnWall" //14
val options = Array("updateStatus","makeFriends","makePost","friendList","getWall")
}

class Client extends Actor
{
  Security.addProvider(new com.sun.crypto.provider.SunJCE());
  val a_sys = context.system
  import a_sys.dispatcher
  import spray.json._
  import spray.util._
  val pipeline_registration = addHeader("Accept", "application/json") ~> sendReceive 
  val pipeline_frndReq = addHeader("Accept", "application/json") ~> sendReceive
  val pipeline_frndList = addHeader("Accept", "application/json") ~> sendReceive
  val pipeline_createPage = addHeader("Accept", "application/json") ~> sendReceive
  val pipeline_pageSubscription = addHeader("Accept", "application/json") ~> sendReceive
  val pipeline_postToPage = addHeader("Accept", "application/json") ~> sendReceive
  val pipleline_writeonWall = addHeader("Accept", "application/json") ~> sendReceive
  var myPuKeys = new HashMap[Int,String]() { override def default(key:Int) = "" }
  var myPrKeys = new HashMap[Int,String]() { override def default(key:Int) = "" }
  var myFriendList = new HashMap[Int,Array[(Int,String)]]{override def default(key:Int) = Array((-99,""))}
  var pageSubscriptionList = new HashMap[(Int,Int),Array[(Int,String)]]{override def default(key:(Int,Int)) = Array((-99,""))}
  //hm+=(22->strPkey)
  
  def genRSAPair(uid:Int):Unit=
    {
      Security.addProvider(new com.sun.crypto.provider.SunJCE());
      val kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      val kp = kpg.genKeyPair();
      val publicKey = kp.getPublic();
      val privateKey = kp.getPrivate();
      val fact = KeyFactory.getInstance("RSA");
      /*Save the pair in a data-structure*/
      val strPukey = new BASE64Encoder().encode(publicKey.getEncoded());
      val strPrkey = new BASE64Encoder().encode(privateKey.getEncoded());
      myPuKeys+=(uid->strPukey)
      myPrKeys+=(uid->strPrkey)   
    }
  
  def digitallySign(uid:Int,msg:String):String=
  {
    Security.addProvider(new com.sun.crypto.provider.SunJCE());
    val fact = KeyFactory.getInstance("RSA");
    val prKeyBytes =  new BASE64Decoder().decodeBuffer(myPrKeys(uid))
    val spec = new PKCS8EncodedKeySpec(prKeyBytes);
    val prKey = fact.generatePrivate(spec);
    val cipher = Cipher.getInstance("RSA");
    cipher.init(Cipher.ENCRYPT_MODE, prKey);
    var byteDataToEncrypt = msg.getBytes();
    val byteCipherText = cipher.doFinal(byteDataToEncrypt);
    var strCipherText = new BASE64Encoder().encode(byteCipherText);
    println("Input msg "+msg)
    println("encrypted text  "+strCipherText)
    
    /*Test decryption locally using public key after encryption using private key.*/
    //------------------------------------------------------------------------------//
    val puKeyBytes =  new BASE64Decoder().decodeBuffer(myPuKeys(uid))
    val puspec = new X509EncodedKeySpec(puKeyBytes);
    val puKey = fact.generatePublic(puspec);
    cipher.init(Cipher.DECRYPT_MODE, puKey);
    val byteDecipherText = new BASE64Decoder().decodeBuffer(strCipherText);
    val byteDecryptedText = cipher.doFinal(byteDecipherText);
    val strDecryptedText = new String(byteDecryptedText);
    println("decrypted text  "+strDecryptedText)
    //------------------------------------------------------------------------------//
    return strCipherText
  }
  
  def receive =
  {
    case register(uid:Int)=>
      {
            val uri = utils.register_uri
            val mypipeline = pipeline_registration 
            import registrationProtocol._
            import spray.json._
            import spray.util._
            genRSAPair(uid)
            val response = mypipeline{Post(uri,registerMsg(uid.toString(),uid,myPuKeys(uid)) )}
            response onComplete {
            case Success(result) =>
              {
                println(result)
              }
       
            case Failure(error) =>
              println("Error registering User")
          }
        
      }
    case frndReq(initiator,target)=>
      {
            val uri = utils.makeFriends_uri
            val mypipeline = pipeline_frndReq 
            import friendRequestProtocol._
            import spray.json._
            import spray.util._
            val hash_targetUID = org.apache.commons.codec.digest.DigestUtils.sha256Hex(target.toString())
            val ds = digitallySign(initiator,hash_targetUID)
            val response = mypipeline{Post(uri,friendRequest(initiator, target,ds) )}
           response onComplete {
            case Success(result) =>
              {
                println("Friend request submitted to system")
              }
       
            case Failure(error) =>
              println("Error sending friend request")
          }
      }
    case  getFrndList(uid:Int)=>
      {
            val uri = utils.friendList_uri
            println(uri)
            import secureFriendListProtocol._
            import spray.json._
            import spray.util._
            val mypipeline =addHeader("Accept", "application/json") ~> sendReceive ~> unmarshal[returnSecureFriendList]
            import secureFriendListRequestProtocol._
            import spray.json._
            import spray.util._
            //val ds = digitallySign(uid,uid.toString())
            val ds = "kjhdkjfh"
            val response = mypipeline{Get(uri, secureFriendListRequest(uid,ds) )}
            //import secureFriendListProtocol._
           response onComplete {
            case Success(result) =>
              {
                //println(result.friendList(0))
                println(result.toJson.prettyPrint)
                myFriendList+=(uid->result.friendList)
                //println(response.friendList(0)._1)
                //println(response.friendList(0)._2)
              }
       
            case Failure(error) =>
              println("Could not retrieve friendList")
          }
      }
    case updateStatus(uid:Int,msg:String)=>
      {
          /*Either use a cached copy of friendList to get friends with whom PubKey the Key will be encrypted
           * or get the Friend list and than use it to make the Post*/
        import SecureStatusUpdateProtocol._
        import spray.json._
        import spray.util._
        val mypipeline =addHeader("Accept", "application/json") ~> sendReceive 
        val uri = utils.updateStatus_uri
        /*generate SecureRandom AES keys for encryption
         * encrypt the msg
         * take sha of msg and digitally sign
         * encrypt the key with public key of friends
         * send the package together*/
         Security.addProvider(new com.sun.crypto.provider.SunJCE());
         var strDataToEncrypt = msg
         var strCipherText = new String();
         var strDecryptedText = new String();
         val keyGen:KeyGenerator = KeyGenerator.getInstance("AES");
         keyGen.init(256);
         val secretKey = keyGen.generateKey();                         
         val aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
         aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey);
         var byteDataToEncrypt = strDataToEncrypt.getBytes();
         val byteCipherText = aesCipherForEncryption.doFinal(byteDataToEncrypt);
         strCipherText = new BASE64Encoder().encode(byteCipherText);//---------------- Encrypted MSG
         println("Cipher Text generated using AES with CBC mode and PKCS5 Padding is " +strCipherText);
         val hash_msg = org.apache.commons.codec.digest.DigestUtils.sha256Hex(strCipherText)
         val ds = digitallySign(uid,hash_msg) // ------------------------- DIGITAL SIGNATURE        
         /*encrypt the key with public key
          * convert to string
          * do the same with iv*/ 
         var iv = aesCipherForEncryption.getIV
         var strIv = new BASE64Encoder().encode(iv)//----------------------- IV in String Format
         var KeyArray = new Array[(Int,String)](myFriendList(uid).length+1)
         var KeyArrayIndex = 0
         val cipher = Cipher.getInstance("RSA");
         val fact = KeyFactory.getInstance("RSA");
         /* In case I had no friends I get a dummy array with one false tuple
          * from the service, so the size of keyArray in that case is 2
          * this had to be done beacuse of json parsing troubles*/
         for (i <- 0 until myFriendList(uid).length)
         {
           if (myFriendList(uid)(i)._1 > 0)
           {
             var strPubKey = myFriendList(uid)(i)._2
             var PubkeyBytes = new BASE64Decoder().decodeBuffer(strPubKey);
             var spec = new X509EncodedKeySpec(PubkeyBytes)
             var targetpublicKey = fact.generatePublic(spec)
             cipher.init(Cipher.ENCRYPT_MODE, targetpublicKey)
             var encryptedBytes = cipher.doFinal(secretKey.getEncoded());
             var str_encrypted_key = new BASE64Encoder().encode(encryptedBytes)
             KeyArray(KeyArrayIndex) =( myFriendList(uid)(i)._1, str_encrypted_key)
             KeyArrayIndex+=1
           }
           else
           {
             KeyArray(KeyArrayIndex) = ( -99, "")
             KeyArrayIndex+=1
           }
         }
         /* The last encrypted key is my version of Key*/
          var strPubKey = myPuKeys(uid)
          var PubkeyBytes = new BASE64Decoder().decodeBuffer(strPubKey);
          var spec = new X509EncodedKeySpec(PubkeyBytes)
          var targetpublicKey = fact.generatePublic(spec)
          cipher.init(Cipher.ENCRYPT_MODE, targetpublicKey)
          var encryptedBytes = cipher.doFinal(secretKey.getEncoded());
          var str_encrypted_key = new BASE64Encoder().encode(encryptedBytes)
          KeyArray(KeyArrayIndex) = ( uid, str_encrypted_key) // ------------------ Array contains tuple of uid,encryptedKey
         // package everything together
          val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
          val timestamp = new Date()
          val response = mypipeline{Post(uri,SecureStatusUpdate(uid,timestamp.toString,strCipherText,strIv,ds,KeyArray) )}
           response onComplete {
            case Success(result) =>
              {
                println("SecureStatusUpdate submitted to system")
              }
       
            case Failure(error) =>
              println("Error sending SecureStatusUpdate")
          }
      }
    case getSecureWall(uid:Int)=>
      {
        val uri = utils.wall_uri+"/"+uid.toString()
        import secureWallProtocol._
        import spray.json._
        import spray.util._
        val pipeline = sendReceive ~> unmarshal[secureWall]
        val response = pipeline{Get(uri)}
        response onComplete {
            case Success(result) =>
              {
               
                 println(result.items(0).submitterUID)
                //println(response.friendList(0)._1)
                //println(response.friendList(0)._2)
                 // Now is the time to decrypt and print the contents of myWall
                /* Construct my private key from the stored String
                 * Use that key to decrypt the key guard of msg-->AES key
                 * use AES key to decrypt the message*/
                Security.addProvider(new com.sun.crypto.provider.SunJCE());
                val fact = KeyFactory.getInstance("RSA");
                val prKeyBytes =  new BASE64Decoder().decodeBuffer(myPrKeys(uid))
                val spec = new PKCS8EncodedKeySpec(prKeyBytes);
                val prKey = fact.generatePrivate(spec);
                val cipher = Cipher.getInstance("RSA");
                val aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
                cipher.init(Cipher.DECRYPT_MODE, prKey);
                println("----SecureWall of "+result.uid.toString()+" -----")
                for (i <- 0 until result.items.length)
                {
                  println("---------------")
                  val strCipherText = result.items(i).enc_msg
                  val byteKeyToDecrypt = new BASE64Decoder().decodeBuffer(result.items(i).enc_key)
                  val byteDecryptedKey = cipher.doFinal(byteKeyToDecrypt)
                  /*use the above to generate AES key*/
                  val myAESkey = new SecretKeySpec (byteDecryptedKey,"AES" )
                  val myIv = new BASE64Decoder().decodeBuffer(result.items(i).iv)
                  aesCipherForDecryption.init(Cipher.DECRYPT_MODE, myAESkey,new IvParameterSpec(myIv));
                  val msgbyteDecipherText = new BASE64Decoder().decodeBuffer(strCipherText)
                  val msgbyteDecryptedText = aesCipherForDecryption.doFinal(msgbyteDecipherText);
                  val msgstrDecryptedText = new String(msgbyteDecryptedText);
                  println(result.items(i).submitterUID.toString+"\n"+result.items(i).timestamp+"\n"+msgstrDecryptedText)
                  println("---------------")
                  
                }
                 
              }//Success
                     
            case Failure(error) =>
              println("Could not retrieve SecureWall")
          }
      }
     case createPage(uid:Int,pid:Int,name:String,topic:String)=>
      {
            val uri = utils.createPage_uri
            val mypipeline = pipeline_createPage 
            import createSecurePageProtocol._
            import spray.json._
            import spray.util._
            val response = mypipeline{Post(uri,createSecurePage(name,topic,pid:Int,uid,digitallySign(uid,pid.toString)) )}
            response onComplete {
            case Success(result) =>
              {
                println(result)
              }
       
            case Failure(error) =>
              println("Error creating Page")
          }
        
      }
    case pageSubscription(requestorID:Int,ownerID:Int,pageID:Int)=>
      {
        val uri = utils.pageSubscription_uri
        val mypipeline = pipeline_pageSubscription
        import subscribeToPageProtocol._
        import spray.json._
        import spray.util._
        //subscribeToPage(requestor_uid:Int,owner_uid:Int,page_Id:Int,requestor_pubKey:String)
        val response = mypipeline{Post(uri,subscribeToPage(requestorID,ownerID,pageID,myPuKeys(requestorID)) )}
            response onComplete {
            case Success(result) =>
              {
                println(result)
              }
       
            case Failure(error) =>
              println("Error subscribing to Page")
          }
      }
    case getSubscribers(requestorID:Int,ownerID:Int,pageID:Int)=>
      {
        /* case class getSubscriptionList(requestor_uid:Int,owner_uid:Int,page_id:Int,digiSign:String)
object getSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val getSubscriptionListFormat = jsonFormat4(getSubscriptionList)
}

case class retSubscriptionList(subscribers:Array[(Int,String)])
object retSubscriptionListProtocol extends DefaultJsonProtocol{
  implicit val retSubscriptionListFormat = jsonFormat1(retSubscriptionList)
}*/
        //val pipeline_subscribers = addHeader("Accept", "application/json") ~> sendReceive ~> unmarshal[returnSecureFriendList]
            import getSubscriptionListProtocol._
            import retSubscriptionListProtocol._
            import spray.json._
            import spray.util._
            val uri = utils.getSubscribers_uri
            val mypipeline =addHeader("Accept", "application/json") ~> sendReceive ~> unmarshal[retSubscriptionList]
            val ds = digitallySign(requestorID,pageID.toString())
            val response = mypipeline{Get(uri, getSubscriptionList(requestorID,ownerID,pageID,ds) )}
            //import secureFriendListProtocol._
           response onComplete {
            case Success(result) =>
              {
                pageSubscriptionList+=((ownerID,pageID)->result.subscribers)
                println(result.subscribers(0))
              }
       
            case Failure(error) =>
              println("Could not retrieve friendList")
          }
        
      }
    case postToPage(submitterId:Int,ownerID:Int,pageID:Int,msg:String)=>
      {
        /*case class secure_page_post(submitter_uid:Int, timestamp:String, enc_post:String, iv:String, key_Array:Array[(Int,String)])
case class securePostToPage(page_post:secure_page_post,page_id:Int,owner_id:Int,digiSign:String)
object securePostToPageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val securePostToPageFormat = jsonFormat4(securePostToPage)
}
*/      /* 1. Encrypt the message with a new AES key*/
         Security.addProvider(new com.sun.crypto.provider.SunJCE());
         var strDataToEncrypt = msg
         var strCipherText = new String();
         var strDecryptedText = new String();
         val keyGen:KeyGenerator = KeyGenerator.getInstance("AES");
         keyGen.init(256);
         val secretKey = keyGen.generateKey();  //------------------------------ Random AES KEY                       
         val aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
         aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey);
         var byteDataToEncrypt = strDataToEncrypt.getBytes();
         val byteCipherText = aesCipherForEncryption.doFinal(byteDataToEncrypt);
         strCipherText = new BASE64Encoder().encode(byteCipherText);//---------------- Encrypted MSG
         println("Cipher Text generated using AES with CBC mode and PKCS5 Padding is " +strCipherText);
         val hash_msg = org.apache.commons.codec.digest.DigestUtils.sha256Hex(strCipherText)
         val ds = digitallySign(submitterId,hash_msg) // ------------------------- DIGITAL SIGNATURE        
         /*encrypt the key with public key
          * convert to string
          * do the same with iv*/ 
         var iv = aesCipherForEncryption.getIV
         var strIv = new BASE64Encoder().encode(iv)//----------------------- IV in String Format
         var KeyArray = new Array[(Int,String)](pageSubscriptionList((ownerID,pageID)).length)
         var KeyArrayIndex = 0
         val cipher = Cipher.getInstance("RSA");
         val fact = KeyFactory.getInstance("RSA");
         /* In case I had no friends I get a dummy array with one false tuple
          * from the service, so the size of keyArray in that case is 2
          * this had to be done beacuse of json parsing troubles*/
         for (i <- 0 until pageSubscriptionList((ownerID,pageID)).length)
         {
             var subscriber_uid = pageSubscriptionList((ownerID,pageID))(i)._1
             var strPubKey = pageSubscriptionList((ownerID,pageID))(i)._2
             var PubkeyBytes = new BASE64Decoder().decodeBuffer(strPubKey);
             var spec = new X509EncodedKeySpec(PubkeyBytes)
             var targetpublicKey = fact.generatePublic(spec)
             cipher.init(Cipher.ENCRYPT_MODE, targetpublicKey)
             var encryptedBytes = cipher.doFinal(secretKey.getEncoded());
             var str_encrypted_key = new BASE64Encoder().encode(encryptedBytes)
             KeyArray(KeyArrayIndex) =( subscriber_uid, str_encrypted_key) //------------------ Array contains (ID,encKey) tuple for post
             KeyArrayIndex+=1          
         }
         val uri = utils.postToPage_uri
         val pipeline = pipeline_postToPage
         val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
         val timestamp = new Date().toString
         import spray.json._
         import spray.util._
         import securePostToPageProtocol._
         val post = secure_page_post(submitterId, timestamp, strCipherText, strIv, KeyArray)
         val response = pipeline{Post(uri,securePostToPage(post,pageID,ownerID,ds))}
           response onComplete {
            case Success(result) =>
              {
                println(result)
              }
       
            case Failure(error) =>
              println("Error posting to Page")
          }
         
      }
    case getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)=>
      {
        /*case class ret_secure_page(name:String,topic:String,creator_uid:Int,created:String,contents:Array[secure_page_post])
object ret_secure_pageProtocol extends DefaultJsonProtocol{
  implicit val secure_page_postFormat = jsonFormat5(secure_page_post)
  implicit val ret_secure_pageFormat = jsonFormat5(ret_secure_page)
}*/
        val uri = utils.getPage_uri+"/"+pageID.toString+"/"+ownerID.toString+"/"+requestorID.toString
        import ret_secure_pageProtocol._
        import spray.json._
        import spray.util._
        val pipeline = sendReceive ~> unmarshal[ret_secure_page]
        val response = pipeline{Get(uri)}
        response onComplete {
            case Success(result) =>
              {              
                 // Now is the time to decrypt and print the contents of Page
                /* Construct my private key from the stored String
                 * Use that key to decrypt the key guard of msg-->AES key
                 * use AES key to decrypt the message*/
                Security.addProvider(new com.sun.crypto.provider.SunJCE());
                val fact = KeyFactory.getInstance("RSA");
                val prKeyBytes =  new BASE64Decoder().decodeBuffer(myPrKeys(requestorID))
                val spec = new PKCS8EncodedKeySpec(prKeyBytes);
                val prKey = fact.generatePrivate(spec);
                val cipher = Cipher.getInstance("RSA");
                val aesCipherForDecryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
                cipher.init(Cipher.DECRYPT_MODE, prKey);
                println(result.name + " "+result.topic+" "+result.creator_uid.toString+" "+result.created)
                for (i <- 0 until result.contents.length)
                {
                  println("---------------")
                  val strCipherText = result.contents(i).enc_post
                  /* Locate my version of encrypted AES key for the post*/
                  var myencAESKey = ""
                  for (j <- 0 until result.contents(i).key_Array.length)
                  {
                    if (requestorID == result.contents(i).key_Array(j)._1)
                    {
                      myencAESKey = result.contents(i).key_Array(j)._2
                    }
                  }
                  if (myencAESKey != "")
                  {
                    val byteKeyToDecrypt = new BASE64Decoder().decodeBuffer(myencAESKey)
                    val byteDecryptedKey = cipher.doFinal(byteKeyToDecrypt) //--------------Decrypt AES key with my private Key
                    /*use the above to generate AES key*/
                    val myAESkey = new SecretKeySpec (byteDecryptedKey,"AES" )
                    val myIv = new BASE64Decoder().decodeBuffer(result.contents(i).iv)
                    aesCipherForDecryption.init(Cipher.DECRYPT_MODE, myAESkey,new IvParameterSpec(myIv));
                    val msgbyteDecipherText = new BASE64Decoder().decodeBuffer(strCipherText)
                    val msgbyteDecryptedText = aesCipherForDecryption.doFinal(msgbyteDecipherText);//---Decrypt msg with the decrypted AES key
                    val msgstrDecryptedText = new String(msgbyteDecryptedText);
                    println(result.contents(i).submitter_uid.toString+"\n"+result.contents(i).timestamp+"\n"+msgstrDecryptedText)
                    println("---------------")
                  }
                  
                }
                 
              }//Success
                     
            case Failure(error) =>
              println("Could not retrieve SecureWall")
          }
      }
    case secureWriteOnWall(myUID:Int,targetUID:Int,msg:String)=>
      {
        /*case class SecureWriteOnWall(targetUID:Int,myUID:Int,payload:String,timestamp:String,iv:String,encKey:String,digiSign:String)
object SecureWriteOnWallProtocol extends DefaultJsonProtocol{
  implicit val SecureWriteOnWallFormat = jsonFormat7(SecureWriteOnWall)
}
case class SecureWriteOnWallWrapper(post:SecureWriteOnWall,context:RequestContext)*/
        import SecureWriteOnWallProtocol._
        import spray.json._
        import spray.util._
        val mypipeline = pipleline_writeonWall
        val uri = utils.writeOnwall_uri
        /*generate SecureRandom AES keys for encryption
         * encrypt the msg
         * take sha of msg and digitally sign
         * encrypt the key with public key of friend
         * send the package together*/
         Security.addProvider(new com.sun.crypto.provider.SunJCE());
         var strDataToEncrypt = msg
         var strCipherText = new String();
         var strDecryptedText = new String();
         val keyGen:KeyGenerator = KeyGenerator.getInstance("AES");
         keyGen.init(256);
         val secretKey = keyGen.generateKey();                         
         val aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
         aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey);
         var byteDataToEncrypt = strDataToEncrypt.getBytes();
         val byteCipherText = aesCipherForEncryption.doFinal(byteDataToEncrypt);
         strCipherText = new BASE64Encoder().encode(byteCipherText);//---------------- Encrypted MSG
         println("Cipher Text generated using AES with CBC mode and PKCS5 Padding is " +strCipherText);
         val hash_msg = org.apache.commons.codec.digest.DigestUtils.sha256Hex(strCipherText)
         val ds = digitallySign(myUID,hash_msg) // ------------------------- DIGITAL SIGNATURE        
         /*encrypt the key with public key
          * convert to string
          * do the same with iv*/ 
         var iv = aesCipherForEncryption.getIV
         var strIv = new BASE64Encoder().encode(iv)//----------------------- IV in String Format
         val cipher = Cipher.getInstance("RSA");
         val fact = KeyFactory.getInstance("RSA");
         var strPubKey = ""
         for (i <- 0 until myFriendList(myUID).length)
         {
           if (myFriendList(myUID)(i)._1 == targetUID)
           {
             strPubKey = myFriendList(myUID)(i)._2
           }
         }
         if (strPubKey != "" )
         {
           var PubkeyBytes = new BASE64Decoder().decodeBuffer(strPubKey);
           var spec = new X509EncodedKeySpec(PubkeyBytes)
           var targetpublicKey = fact.generatePublic(spec)
           cipher.init(Cipher.ENCRYPT_MODE, targetpublicKey)
           var encryptedBytes = cipher.doFinal(secretKey.getEncoded());
           var str_encrypted_key = new BASE64Encoder().encode(encryptedBytes) //--------------- Encrypted Key
           val dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
           val timestamp = new Date().toString
           val post = SecureWriteOnWall(targetUID,myUID,strCipherText,timestamp,strIv,str_encrypted_key,ds)
           val response = mypipeline{Post(uri,post)}
             response onComplete {
              case Success(result) =>
                {
                  println(result)
                }
         
              case Failure(error) =>
                println("Error writing to Wall")
            }
           }
         else
         {
           println("I do not have the public key of target Friend, cannot write to his Wall.")
         }
      }
      case getProfile(targetUID:Int)=>
      {
        
         implicit val timeout = new Timeout(5 seconds)
         val uri = utils.getProfile_uri+"/"+targetUID.toString()
         //val mypipeline = pipelines(uid%pipelines.length)
         val mypipeline = sendReceive
         val response = mypipeline{Post(uri)}
         response onComplete {
           case Success(result) =>
            {
                println(result)
            }
            case Failure(error) =>
              println("Cannot retrieve Profile")
          } 
        
      }
    case `testSignature`=>
      {
        genRSAPair(55)
        digitallySign(55,"356")
      }
  }
}

object secureClient {
   def main(args: Array[String])
    {
       implicit val system = ActorSystem("secure-client")
       val client1 = system.actorOf(Props(new Client()),"client1") 
       val client55 = system.actorOf(Props(new Client()),"client55") 
       val client56 = system.actorOf(Props(new Client()),"client56") 
       val client57 = system.actorOf(Props(new Client()),"client57") 
       val client58 = system.actorOf(Props(new Client()),"client58") 
       val client80 = system.actorOf(Props(new Client()),"client80") 
       val a_sys = system
       import a_sys.dispatcher
       /*a_sys.scheduler.scheduleOnce(new FiniteDuration(10,MILLISECONDS),client1,register(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,MILLISECONDS),client1,register(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,MILLISECONDS),client1,register(57))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,MILLISECONDS),client1,frndReq(55,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,MILLISECONDS),client1,frndReq(57,56))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client1,getFrndList(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client1,getFrndList(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client1,secureWriteOnWall(55,56,"Hi 56 This is 55 on your Wall"))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client1,getFrndList(57))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client1,updateStatus(55,"my name is Richard Dawkins."))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(13,SECONDS),client1,updateStatus(57,"Hi !! I am Jimmy Johns number 57."))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client1,getSecureWall(56))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(40,MILLISECONDS),client1,createPage(56,128,"56's page","Security in Social Client by 56"))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client1,pageSubscription(55,56,128))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client1,pageSubscription(57,56,128))
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client1,getSubscribers(55,56,128))
       //postToPage(submitterId:Int,ownerID:Int,pageID:Int,msg:String)
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client1,postToPage(55,56,128,"55 posted to Page 128 started by 56"))
       //getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(40,SECONDS),client1,getSecurePage(128,56,55))
       //client1 ! register(55)
       //client1 ! register(56)
       //client1 ! frndReq(55,56)
        //client1 ! getFrndList(55)
       //client1 ! frndReq
       //client1 ! testSignature
       //client1 ! updateStatus(123,"my name is Richard Dawkins.")*/
       
       /*--------------------------*/
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client55,register(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client56,register(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(40,MILLISECONDS),client56,createPage(56,128,"56's page","Security in Social Client by 56"))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client57,register(57))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client58,register(58))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,MILLISECONDS),client55,frndReq(55,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(40,MILLISECONDS),client57,frndReq(57,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(50,MILLISECONDS),client58,frndReq(58,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client55,pageSubscription(55,56,128))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client57,pageSubscription(57,56,128))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,SECONDS),client58,pageSubscription(58,56,128))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client56,getFrndList(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client55,getFrndList(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client55,updateStatus(55,"my name is 55 and my UID is 55."))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client56,updateStatus(56,"my name is 56 and my UID is 56."))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client55,secureWriteOnWall(55,56,"Hi 56 This is 55 on your Wall"))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,SECONDS),client56,getSecureWall(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,SECONDS),client55,getSubscribers(55,56,128))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(40,SECONDS),client55,postToPage(55,56,128,"55 posted to Page 128 started by 56"))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(50,SECONDS),client56,getSecurePage(128,56,56))//getSecurePage(pageID:Int,ownerID:Int,requestorID:Int)
       a_sys.scheduler.scheduleOnce(new FiniteDuration(55,SECONDS),client56,getProfile(55))
     
       
    }
  
}


/*
 * /*Either use a cached copy of friendList to get friends with whom PubKey the Key will be encrypted
           * or get the Friend list and than use it to make the Post*/
        import SecureStatusUpdateProtocol._
        import spray.json._
        import spray.util._
        val mypipeline =addHeader("Accept", "application/json") ~> sendReceive 
        /*generate SecureRandom AES keys for encryption
         * encrypt the msg
         * take sha of msg and digitally sign
         * encrypt the key with public key of friends
         * send the package together*/
         Security.addProvider(new com.sun.crypto.provider.SunJCE());
         var strDataToEncrypt = msg
         var strCipherText = new String();
         var strDecryptedText = new String();
         val keyGen:KeyGenerator = KeyGenerator.getInstance("AES");
         keyGen.init(256);
         val secretKey = keyGen.generateKey();                         
         val aesCipherForEncryption = Cipher.getInstance("AES/CBC/PKCS5PADDING"); 
         aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey);
         var byteDataToEncrypt = strDataToEncrypt.getBytes();
         val byteCipherText = aesCipherForEncryption.doFinal(byteDataToEncrypt);
         strCipherText = new BASE64Encoder().encode(byteCipherText);
         println("Cipher Text generated using AES with CBC mode and PKCS5 Padding is " +strCipherText);
         
         /*encrypt the key with public key
          * convert to string
          * do the same with iv*/ 
         val kpg = KeyPairGenerator.getInstance("RSA");
         kpg.initialize(2048);
         val kp = kpg.genKeyPair();
         var publicKey = kp.getPublic();
         val privateKey = kp.getPrivate();
         val fact = KeyFactory.getInstance("RSA");
         val cipher = Cipher.getInstance("RSA");
         cipher.init(Cipher.ENCRYPT_MODE, publicKey);
         var RSAbyteDataToEncrypt = secretKey.getEncoded()
         
         println(new String(RSAbyteDataToEncrypt))
         val RSAbyteCipherText = cipher.doFinal(RSAbyteDataToEncrypt);
         var RSAstrCipherText = new BASE64Encoder().encode(RSAbyteCipherText);
         
         
         /* Local test use the above information to decrypt*/
         cipher.init(Cipher.DECRYPT_MODE, privateKey);
         val byteDecipherText = new BASE64Decoder().decodeBuffer(RSAstrCipherText);
         val byteDecryptedText = cipher.doFinal(byteDecipherText);
         println(new String(byteDecryptedText))
         println(byteDecryptedText.deep == RSAbyteDataToEncrypt.deep)
         
         var myAESkey = new SecretKeySpec (byteDecryptedText,"AES" )
         println(myAESkey.getEncoded().deep == secretKey.getEncoded().deep)
         var iv = aesCipherForEncryption.getIV
         var strIv = new BASE64Encoder().encode(iv)
         val myIv = new BASE64Decoder().decodeBuffer(strIv)
         aesCipherForEncryption.init(Cipher.DECRYPT_MODE, myAESkey,new IvParameterSpec(myIv))
         
         val msgbyteDecipherText = new BASE64Decoder().decodeBuffer(strCipherText)
         val msgbyteDecryptedText = aesCipherForEncryption.doFinal(msgbyteDecipherText);
         val msgstrDecryptedText = new String(msgbyteDecryptedText);
         println("#######     "+msgstrDecryptedText)
         
         
         //var strKey = secretKey.toString()
         //var iv = aesCipherForEncryption.getIV
         //var strIV = iv.toString()
        
        /*calculate SHA of msg and digitally sign it*/
          //val shaMsg = org.apache.commons.codec.digest.DigestUtils.sha256Hex(msg)
          //val ds = digitallySign(uid,shaMsg)
           */

  /*--------------------------*/
       /*a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client55,register(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client56,register(56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client57,register(57))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client58,register(58))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client57,register(57))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(5,MILLISECONDS),client80,register(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(30,MILLISECONDS),client55,frndReq(55,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(40,MILLISECONDS),client57,frndReq(57,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(50,MILLISECONDS),client58,frndReq(58,56))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(10,SECONDS),client55,getFrndList(55))
       a_sys.scheduler.scheduleOnce(new FiniteDuration(20,SECONDS),client55,secureWriteOnWall(55,56,"Hi 56 This is 55 on your Wall"))*/
       //a_sys.scheduler.scheduleOnce(new FiniteDuration(30,SECONDS),client80,getSecureWall(56))
 