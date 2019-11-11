# A demo project of Spring-Cloud-Stream and Rabbit MQ

A demo project of highly available message application with Spring-Cloud-Stream and Rabbit MQ

<b>Table of Contents:</b>
- [The curse of Distributed System](#distributed_system)
- [Resilience Best Practice to achieve Reliability with RabbitMQ](#resilience_reilability)
    - [Acknowledgement and Confirm](#acknowledgement_confirms) 
    - [Heartbeat and Keep-Alive](#heartbeat_keepalive)
    - [Durability on Message Broker](#durability)
    - [Clustering and Message Replication](#message_replication)
    - [Replication Factor: How Many Mirrors are Optimal?](#replication_factor)
    - [Queue Synchronisation Mode](#queue_synchronisation)
    - [Connecting to Clusters from Clients](#connecting_cluster)
- [Pre-requisite](#pre_requisite)    
    - [Rabbit MQ Command](#rabbitmq_command)
    - [Rabbit MQ Config File](#rabbitmq_config)
    - [Rabbit MQ Log File](#rabbitmq_log)
    - [RabbitMQ Management UI](#rabbitmq_mgmt_ui)
    - [Allow Remote Access to Management UI](#rabbitmq_mgmt_ui_remote_access)
    - [Allow Remote Access using AMQP client](#amqp_remote_access)
    - [Setup RabbitMQ cluster in local machine](#setup_rabbitmq_cluster)

<br/>

### <a name="distributed_system"></a> The curse of Distributed System
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

### <a name="resilience_reilability"></a>Resilience Best Practice to achieve Reliability with RabbitMQ
Every software developer aims to create a reliable system, and so reliability is the result everyone aims for.
In reality, we need to apply various architecture patterns or engineering approaches in order to achieve the 
target result, and the ability of the system to withstand/recover from different types of failure is known 
as resiliency. The better the resilience measure built into the system, usually gets better reliability.

Reliability in a messaging system means application developers and system operators deliver the message in a
reliable manner, that is, to ensure that messages are always delivered, even encountering failures of various 
kinds.

#### <a name="acknowledgement_confirms"></a>Acknowledgement and Confirm 
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

#### <a name="heartbeat_keepalive"></a>Heartbeat and Keep-Alive
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

#### <a name="durability"></a>Durability on Message Broker
In order to avoid losing messages in the broker, queues and messages must be able to cope with 
broker restarts, broker hardware failure and in extremis even broker crashes.

Durability of a queue does not make messages that are routed to that queue durable. If broker 
is taken down and then brought back up, durable queue will be re-declared during broker startup, 
however, only persistent messages will be recovered. 

#### <a name="message_replication"></a>Clustering and Message Replication
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

#### <a name="policy"></a>Policy
Mirroring parameters are configured using policies. To cause queues to become mirrored, you need to 
create a policy which matches them and sets policy keys ha-mode and (optionally) ha-params. 

##### <a name="replication_factor"></a>Replication Factor: How Many Mirrors are Optimal?
Mirroring to all nodes is the most conservative option. 

Mirroring to a quorum (N/2 + 1) of cluster nodes is recommended instead. Mirroring to all nodes will 
put additional strain on all cluster nodes, including network I/O, disk I/O and disk space usage. Having 
a replica on every node is unnecessary in most cases.

For clusters of 3 and more nodes it is recommended to replicate to a quorum (the majority) of nodes, 
e.g. 2 nodes in a 3 node cluster or 3 nodes in a 5 node cluster.

See also:
- [High availability via mirrored queues](https://www.rabbitmq.com/ha.html)

##### <a name="queue_synchronisation"></a>Queue Synchronisation Mode
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

##### <a name="connecting_cluster"></a>Connecting to Clusters from Clients
A client can connect as normal to any node within a cluster. If that node should fail, and the 
rest of the cluster survives, then the client should notice the closed connection, and should 
be able to reconnect to some surviving member of the cluster. Generally, it's not advisable 
to bake in node hostnames or IP addresses into client applications: this introduces inflexibility 
and will require client applications to be edited, recompiled and redeployed should the configuration 
of the cluster change or the number of nodes in the cluster change. Instead, we recommend a more 
abstracted approach: this could be a dynamic DNS service which has a very short TTL configuration, 
or a plain TCP load balancer.

##### <a name="pre_requisite"></a>Pre-requisite
To develop/debug locally, install RabbitMQ on MAC using Homebrew.

On a Mac with homebrew:
```sh        
brew install rabbitmq
```
Once completed, launch it with default settings.
```sh
/usr/local/Cellar/rabbitmq/3.6.14/sbin/rabbitmq-server
```

Default startup port will be `5672`.

##### <a name="rabbitmq_command"></a>Rabbit MQ Command
For installation on MAC using Homebrew, the binaries are installed under `/usr/local/Cellar/rabbitmq/3.6.14/`.
Add the `sbin` folder to system path

##### <a name="rabbitmq_config"></a>Rabbit MQ Config File
For installation on MAC using Homebrew, config file is located under:
`/usr/local/etc/rabbitmq/rabbitmq.conf`
This file usually doesn't exist after fresh installation, need to be created manually.

##### <a name="rabbitmq_log"></a>Rabbit MQ Log File
For installation on MAC using Homebrew, log file is located under:
`/usr/local/var/log/rabbitmq/`

##### <a name="rabbitmq_mgmt_ui"></a>RabbitMQ Management UI
Browse to `http://localhost:15672`, login with default user name `guest` and password `guest`.

##### <a name="rabbitmq_mgmt_ui_remote_access"></a>Allow Remote Access to Management UI
By default, the guest user is prohibited from connecting to the broker remotely. Create a new admin user, as example below:
```sh
rabbitmqctl add_user dev dev
rabbitmqctl set_user_tags dev administrator
rabbitmqctl set_permissions -p / dev ".*" ".*" ".*"
```

##### <a name="amqp_remote_access"></a>Allow Remote Access using AMQP client 
By default, the `rabbitmq-env.conf` file contained an entry for NODE_IP_ADDRESS to bind it only to localhost. Modify the 
NODE_IP_ADDRESS entry from the config as below such that it binds the port to all network interfaces.
```sh
NODE_IP_ADDRESS=0.0.0.0
```
After updating the configuration, restart RabbitMQ.

If RabbitMQ cannot be restarted, use the following command to verify if RabbitMQ process still running:
```sh
ps aux | grep epmd
ps aux | grep erl
```
Then restart RabbitMQ with following command:
```sh
rabbitmqctl shutdown
rabbitmq-server
```

##### <a name="setup_rabbitmq_cluster"></a>Setup RabbitMQ cluster in local machine 
 Adding a new Rabbit node on Mac OSX (Rabbit MQ before v3.7 using Erlang based configuration) 
- Create a shell script:
    ```bash
    #!/bin/bash
    export RABBITMQ_CONF_ENV_FILE=/Users/xxx/tmp/rabbitmq-env.config
    clear && /usr/local/Cellar/rabbitmq/3.6.14/sbin/rabbitmq-server
    ```
- Create the RabbitMQ environment config file:
    ```sh
    CONFIG_FILE=/Users/xxx/tmp/rabbitmq
    NODE_IP_ADDRESS=0.0.0.0
    NODENAME=rabbit_n1@HOSTNAME
    NODE_PORT=5674
    ```
- Customize the ports for management console in the rabbitmq.config file, based on the []class config file](https://github.com/rabbitmq/rabbitmq-server/blob/v3.7.x/docs/rabbitmq.config.example)
    ```sh
       {listener, [{port,     15674}
    ```
    un-comment the line above
    
- configure the new RabbitMQ node to join the cluster, make sure both the RabbitMQ nodes are started
    ```sh
    rabbitmqctl -n new_node@localhost stop_app
    rabbitmqctl -n new_node@localhost join_cluster old_node@localhost
    rabbitmqctl -n new_node@localhost start_app
    ```