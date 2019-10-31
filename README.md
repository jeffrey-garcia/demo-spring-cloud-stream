# Resilience Best Practice to achieve Reliability with RabbitMQ
Every software developer aims to create a reliable system, and so reliability is the result everyone aims for.
In reality, we need to apply various architecture patterns or engineering approaches in order to achieve the 
target result, and the ability of the system to withstand/recover from different types of failure is known 
as resiliency. The better the resilience measure built into the system, usually gets better reliability.

 
Reliability in a messaging system means application developers and system operators deliver the message in a
reliable manner, that is, to ensure that messages are always delivered, even encountering failures of various 
kinds.

### The curse of Distributed System
Messaging-based systems are distributed by definition and can fail in various ways. For example:
- network connection problems and congestion are probably the most common class of failure.
- the server and client applications can experience hardware failure (or software can crash) at any time 
- logic errors can cause channel or connection errors which force the client to establish a new channel or  
  connection
- omission failures (failure to respond in a predictable amount of time), performance degradations, malicious 
  or poorly written applications that exhaust the system resources
  
In a distributed system, we try to guarantee the delivery of a message by waiting for an acknowledgement 
that it was received, but all sorts of things can go wrong:
- Did the message get dropped? 
- Did the ack get dropped? 
- Did the receiver crash? 
- Are they just slow? 
- Is the network slow?

  
### Acknowledgement and Confirm
When network connection fail, message in-transit over the network will not be delivered to the remote recipient
(this can either be the message broker or the message consumer), in such case is the sender's responsibility 
to resend the message (this can either be the message producer or the message broker) to avoid the message 
is not lost.

But what if the consumer process crashes after receiving a message and right before finish its own logic? 
We might want the message broker resend the message to the consumer so it can retry the business logic.
While under a normal circumstance, there needs to be a way to allow a consumer to indicate to the message 
broker that it has received and/or processed a delivery, otherwise the message will be resent over and over.

Use of acknowledgements guarantees at least once delivery. Without acknowledgements, message loss is 
possible during publish and consume operations and only at most once delivery is guaranteed. Acknowledgements 
can be used in both directions - to allow a consumer to indicate to the server that it has received and/or 
processed a delivery and to allow the server to indicate the same thing to the publisher. Both are known as 
consumer acknowledgements and publisher confirms.

When using confirms, producers recovering from a channel or connection failure should retransmit any 
messages for which an acknowledgement has not been received from the broker. There is a possibility 
of message duplication here, because the broker might have sent a confirmation that never reached 
the producer (due to network failures, etc). Therefore consumers must be prepared to handle deliveries 
they have seen in the past. It is recommended that consumer implementation is designed to be idempotent 
rather than to explicitly perform deduplication.

### Heartbeat and Keep-Alive
Not only can networks fail, firewalls can interrupt connections they consider to be idle. For example,
Certain networking tools (HAproxy, AWS ELB) and equipment (hardware load balancers) may terminate "idle" 
TCP connections when there is no activity on them for a certain period of time.

Use heartbeat to ensure that the application layer promptly finds out about disrupted connections 
(and also completely unresponsive peers), it also defend against certain network equipment which 
may terminate "idle" TCP connections when there is no activity on them for a certain period of time.

Disabling heartbeats is highly discouraged. If heartbeats are disabled, it will make timely peer 
unavailability detection much less likely. That would pose a significant risk to data safety, in 
particular for publishers.

Industry practitioners suggest that a heartbeat timeout within 5 to 15 second range is enough to 
satisfy the defaults of most popular proxies and load balancers, whereas values lower than 5 seconds 
are fairly likely to cause false positives.

### Durability on Message Broker
In order to avoid losing messages in the broker, queues and messages must be able to cope with 
broker restarts, broker hardware failure and in extremis even broker crashes.

Durability of a queue does not make messages that are routed to that queue durable. If broker 
is taken down and then brought back up, durable queue will be re-declared during broker startup, 
however, only persistent messages will be recovered. 

### Clustering and Message Replication
Clusters of nodes offer redundancy and can tolerate failure of a single node. In a RabbitMQ cluster, 
all definitions (of exchanges, bindings, users, etc) are replicated across the entire cluster. 
While queues behave differently, by default residing only on a single node, but can be configured 
to be replicated (mirrored) across multiple nodes.

Each mirrored queue consists of one master and one or more mirrors. The master is hosted on one node 
commonly referred as the master node. Each queue has its own master node. All operations for a given 
queue are first applied on the queue's master node and then propagated to mirrors. This involves 
enqueueing publishes, delivering messages to consumers, tracking acknowledgements from consumers 
and so on.

Mirrored queues replicate their contents across a number of configured cluster nodes. Consumers are 
connected to the master regardless of which node they connect to, with mirrors dropping messages 
that have been acknowledged at the master. Queue mirroring therefore improves fault-tolerant & 
availability, but does not distribute load across nodes (all participating nodes each do all the work).

If the node that hosts queue master fails, queues with master replica hosted on that node under go 
a promotion (new master election), this is usually the oldest mirror. Key reliability criteria in 
this scenario is whether there is a replica (queue mirror) eligible for promotion.

Exclusive queues that are tied to the lifecycle of their connection are never mirrored and by 
definition will not survive a node restart.

##### Policy
Mirroring parameters are configured using policies. To cause queues to become mirrored, you need to 
create a policy which matches them and sets policy keys ha-mode and (optionally) ha-params. 

##### Replication Factor: How Many Mirrors are Optimal?
Mirroring to all nodes is the most conservative option. 

Mirroring to a quorum (N/2 + 1) of cluster nodes is recommended instead. Mirroring to all nodes will 
put additional strain on all cluster nodes, including network I/O, disk I/O and disk space usage. Having 
a replica on every node is unnecessary in most cases.

For clusters of 3 and more nodes it is recommended to replicate to a quorum (the majority) of nodes, 
e.g. 2 nodes in a 3 node cluster or 3 nodes in a 5 node cluster.

##### Queue synchronisation mode
- ha-sync-mode: manual
    this is the default mode. A new queue mirror will not receive existing messages, 
    it will only receive new messages. The new queue mirror will become an exact 
    replica of the master over time, once consumers have drained messages that 
    only exist on the master. If the master queue fails before all unsychronised 
    messages are drained, those messages will be lost. 
    
- ha-sync-mode: automatic
    a queue will automatically synchronise when a new mirror joins.  If queues 
    are small, or you have a fast network between RabbitMQ nodes and the 
    ha-sync-batch-size was optimised, this is a good choice. When enabling 
    automatic queue mirroring, consider the expected on disk data set of the queues 
    involved. Queues with a sizeable data set (say, tens of gigabytes or more) will 
    have to replicate it to the newly added mirror(s), which can put a significant 
    load on cluster resources such as network bandwidth and disk I/O. 

##### Connecting to Clusters from Clients
A client can connect as normal to any node within a cluster. If that node should fail, and the 
rest of the cluster survives, then the client should notice the closed connection, and should 
be able to reconnect to some surviving member of the cluster. Generally, it's not advisable 
to bake in node hostnames or IP addresses into client applications: this introduces inflexibility 
and will require client applications to be edited, recompiled and redeployed should the configuration 
of the cluster change or the number of nodes in the cluster change. Instead, we recommend a more 
abstracted approach: this could be a dynamic DNS service which has a very short TTL configuration, 
or a plain TCP load balancer.