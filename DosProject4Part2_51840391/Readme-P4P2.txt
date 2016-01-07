
Readme- Project4-Part2

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

for client (secure-client)

sbt run


#######################################
Service - High level Design description
#######################################

Service front-end is implemented using spray-can ,spray-http & spray-json
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
back-end system. 

#########################################
Security Implementation-On Client & Service
#########################################
Two aspects of security are handled in the design namely -Authenication and Privacy.
Each user maintains a pair of public/private keys, private keys are local to the user and 
never shared.
When registering with the service a user uploads his public key to the service. Also public
keys are shared with the service when sending friend-requests and page-subscription requests.

After the initial registration all requests are digitally signed by the user using his private key,
the server validates the signature before acting on the request.
For example for a friend request--
A user has to digitally sign the targetID of the friend to which he is sending the Request.

For a status update/write on Wall & posts on a page--
A user computes the Secure Hash of the encrypted post and signs it.

To ensure privacy--All data on the server side is encrypted , Only crypto task at the service
is to validate the digital signatures using public keys.

The below Flow describes how a status update is made similar technique is followed for
write on Walls and page posts--

1. User gets a list of his friends from the service, this list contains the UID and public keys of the friends.
2. User generates a new key,IV using JavaCrypto's native AES key generator that is secure, for every post, ie. each post has a different AES 256 key and IV.
3. That key is used to encrypt the Message.
4. user than iterates over the list of his friends using their publickeys to encrypt the private keys, ie if there are n-friends there are 'n' versions of the 
   encrypted key.
5. IV is sent in clear, as instructed in the Manual.
6. User computes SHA256 of the encrypted post and digitally signs it using his private key.
7. Next he clubs the encrypted post, list of encrypted AES keys, IV and Digital signature together and sends itto the service.
8. Friend uses his private key at his client to decrypt the AES key protecting the post and subsequently uses the AES key to decrypt the post.

Javax-crypto library is used to provide for all cryptographic functions and RSA 2048 and AES 256 are used for asymmetric and symmetric encryption respectively.

#############################################
Functionality Provided
#############################################

1. Register a User and create a default profile.
2. Send a Friend Request
3. get list of friends from the service
4. Make a Status update
5. Write on the Wall of your friend -- i.e. a private msg.
6. create a secure page
7. subscribe to a page.
8. get list of subscribers to a page.
9. make a post on the page.
10. retrieve profile from the service.


