Saumitra Aditya- UFID# 51840391

I started off with using smalller work-units  and then increased it in steps to observe improvement in performance.
smaller work units might not be optimized chunks as there will be more messages flowing between workers 
and Supervisor to manage the job, which could lead to a  overhead. In my set up I used a VPN to connect two machines 
to create the experimental set up and I could readily see that Network bandwidth became the bottleneck---

"""
[WARN] [09/13/2015 13:02:05.941] [RichMiners-akka.remote.default-remote-dispatcher-6] [akka.tcp://RichMiners@172.31.0.101:8000/
system/endpointManager/reliableEndpointWriter-akka.tcp%3A%2F%2FRichMiners%40172.31.0.100%3A8000-0/endpointWriter] [7447047] buffered 
messages in EndpointWriter for [akka.tcp://RichMiners@172.31.0.100:8000]. You should probably implement flow control to avoid 
flooding the remote connection.
"""




sam@ubuntu:~/ScalaProjects/BitCoinMiner$ time scala -classpath "commons-codec-1.10.jar:target/scala-2.11/bitcoinminer_2.11-1.0.jar"  project1 4 > ~/Desktop/scala4.log

real	5m50.948s
user	10m44.636s
sys	0m20.294s
sam@ubuntu:~/ScalaProjects/BitCoinMiner$ ls -ltr src/main/scala/
total 8
-rw-rw-r-- 1 sam sam 3974 Sep 13 15:42 project1.scala~
-rw-rw-r-- 1 sam sam 4006 Sep 13 15:55 project1.scala
sam@ubuntu:~/ScalaProjects/BitCoinMiner$ cat /proc/cpuinfo | grep -i processor
processor	: 0
processor	: 1
sam@ubuntu:~/ScalaProjects/BitCoinMiner$ 

Since I performed part 1 of the experiment on a VM with only two cores, I think I got optimal performance and was able to 
utilize both the cores effectively.

The results are attached in a separate log file .


I was able to mine a coin with 7 leading Zeros--
saumitraadityawhc6yiBWv0YBMVYA1	0000000d9a2bceb9163c1710e76e7cb63cfc0812e91f45643558f196ebc01746

I ran my setup with two machine-one VM with two virtualized cores and one physical machine with 4 cores.



#####################################################################################################################
                                     CODE SUBMISSION
#####################################################################################################################

I am submitting three sbt folders, src code is under src/main/scala

1. BitCoinMiner

To run simply do 
sbt run 4

2. BCMinerClient -- You may have to change application.conf to run it on alocal machine, I used two separate machines

To run--
sbt run <server-IP:Port>

3. BCMinerServer
To run--
sbt run <num zeros>
