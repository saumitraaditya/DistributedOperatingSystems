

Readme- Project4-Part1

################################
Submitter: Saumitra Aditya 
UFID: 51840391
saumitraaditya@ufl.edu
################################

#######################################
The code has been tested on eclipse.

om Terminal

for service -(spray-template)

sbt run

for simulator (number of simulated users = 1000 * number of workers+1000)

sbt "run <number of workers> <number of pipelines>" > log.txt

than use vim/vi to search for STATISTICS--you might have to do iterative search to get to bottom value

Included in social-client folder is a log for run of simulator with parameters -sbt "run 5 10"
#######################################

#######################################
Service - High level Design description
#######################################

Service front-end is implemented using spray-can and spray-http
Back-End is a layer of 1000 working node actors, the servce is 
designed to handle 100000 users, however it can handle more by increasing 
the number of actors.Each user is associated with a UID depending on which,
 the actor which creates and maintains the
in-memory data-structures is selected. All further calls involving this
UID are routed to this back-end actor from the front-end. In case of 
messages/Wall posts to other users, the target actor is selected using
the UID of the friend. Same logic is used to handle the implementation 
for pages. This design ensures that no blocking activity is carried out
at the Front End. Futures are employed when making a Get query to the
back-end system. For some classes spray-json is used for parsing messages
between clients and service.

This following abstractions related end-points are implemented, we take
a look at them using examples. (you can paste them in the browser to try 
them out.)

1. Register a User.

http://localhost:8080/graph/register/556/saumitra
 
 ->User Registered
 
 2. Update Status
 
 http://localhost:8080/graph/updateStatus?uid=556&status=This%20is%20my%20First%20status
 
 3. Write on Wall of a Friend (after users are created)-- In case they are not request will be silently discarded.
 
 http://localhost:8080/graph/sendPost?sender=10277&receiver=10641&post=This_is_post_from_10277_at_Sun_Nov_29_18:18:41_PST_2015_to_10641
 
 -> submitted post from 10277 to 10641
 
 4. View Wall/Time-Line
 
 http://localhost:8080/graph/getWall/556
 
 ->
 {
  "name": "name",
  "items": [{
    "submitterUID": 556,
    "first_receiver_uid": -9999,
    "msg_type": "direct",
    "timeStamp": "Mon Nov 30 15:17:57 PST 2015",
    "content": "This is my First status"
  }]
}

5. Send Friend Request (from 556->1000)

http://localhost:8080/graph/makeFriends/556/1000  

->
Friend request sent

6. View FriendList of a User

http://localhost:8080/graph/friendList/556

->

{
  "friendList": [1000]
}

7. Update profile for a User

curl -i 'http://localhost:8080/graph/updateProfile/556' -X POST -H "Content-Type:application/json" -d '{"uid":556,"name":"admin","sex":"male","location":"gnv","age":20}'

->
HTTP/1.1 200 OK
Server: spray-can/1.3.3
Date: Mon, 30 Nov 2015 23:34:46 GMT
Content-Type: application/json; charset=UTF-8
Content-Length: 23

Profile updated for 556

8.  View profile of a User

http://localhost:8080/graph/getProfile/556  

{
  "name": "admin",
  "location": "gnv",
  "age": 20,
  "sex": "male",
  "uid": 556
}

9. Create a Page

 http://localhost:8080/graph/createPage?name=Page1&topic=Topic1&pid=55&uid=556
 
 ->
 Page creation request submitted to System
 
 10. Post to a Page
 
 http://localhost:8080/graph/postToPage?pid=55&uid=556&post=This%20is%20my%20first%20post%20to%20this%20page
 
 ->
 Post to page submitted to System
 
 11. View a Page.
 
 http://localhost:8080/graph/getPage/55
 
 ->
{
  "name": "Page1",
  "contents": [{
    "submitter_uid": 555,
    "post": "This is my first post to this page",
    "timestamp": "Mon Nov 30 15:40:09 PST 2015"
  }, {
    "submitter_uid": 556,
    "post": "This is my first post to this page",
    "timestamp": "Mon Nov 30 15:40:16 PST 2015"
  }],
  "creator_uid": "556",
  "topic": " ",
  "created": "Mon Nov 30 15:38:39 PST 2015"
}

###############################################################################################################################

There is no authentication implemented in the system, but semantics of abstractions are implemented -- 
for example --

A status update is seen on wall of A's Friends.

if A writes on Wall of B, it is seen by all of A's friends and all of B's Friends in addition to A and B.

A can write to Wall of B only if A is a friend of B.

################################################################################################################################
Design of the Simulator
################################################################################################################################

Functionality of Simulator is also implemented on top of spray-client and a layer of Worker Actors, these worker actors are delegated 
the task of sending API requests on behalf of simulated users. Each Worker actor makes use of a collection of pipelines so as not to
overwhelm the connection and to optimize performance. To achieve a pattern in conformance with the usage statistics available on the
Web - Actor scheduling is used-- i.e. the actors are programmed to periodically make desired API calls with the desired Frequency.

I found the information in below Link more relevant--
http://www.digitalbuzzblog.com/facebook-statistics-stats-facts-2011/

Other sources--
https://zephoria.com/top-15-valuable-facebook-statistics/
http://www.zettasphere.com/mind-boggling-stats-for-1-second-of-internet-activity/
http://www.digitaltrends.com/social-media/nearly-300000-status-updates-are-posted-to-facebook-every-minute/
http://www.digitalbuzzblog.com/facebook-statistics-stats-facts-2011/

To provide a gist here-- In actual facebook deployment in 2011 with 250 Million users online,
1500 status updates are made per second, followed by 1300 Wall posts and around 1500 friend requests.
I would assume time-line views are as frequent as status updates, and one would check profile with the
same frequency as the friend requests are sent.

Given the above Information I configured my system to Max out API calls in the above proportion.

#######################################################################################################################################
Experiment Set-Up and Results
#######################################################################################################################################

I carried out the Experiment in a Ubuntu 14.04 VM running on VMware Workstation 12, with 2 cores and 2GB RAM allocated to it.
Physical machine has i5cpu and 4GB RAM.
All measurements were taken on eclipse running both the simulator and sevice side by side.
I simulated for 11000 users, beyond which the system would not go -- below are the results

The simulator starts by registering 1000*args(0) number of users and than to begin with ensures
every user has two friends-- these are not incuded in statistics.

###############################################################
######################STATISTICS################################
The statistics gathered from this run as as below--
Status-Updates 34286
Posts on other's Walls 17925
Checking Timeliness 28033
FriendRequest 23482
ProfileUpdates 2910
ProfileViews 23499
Net Requests 130135
Run_Duration in Seconds 141
Requests handled per second 922
###############################################################
/*Update Status*/
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
		
 
 


