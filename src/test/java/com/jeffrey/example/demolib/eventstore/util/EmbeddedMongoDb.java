package com.jeffrey.example.demolib.eventstore.util;

import com.mongodb.BasicDBList;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.bson.Document;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedMongoDb {

    public static final String DEFAULT_CONN_STR =
            "mongodb://localhost:27017,localhost:27018/test?replicaSet=replocal&w=majority&wtimeoutMS=2000";

    public static ReplicaSetConfigurer replicaSetConfigurer() {
        return ReplicaSetConfigurer.instance;
    }

    private EmbeddedMongoDb() {}

    public static class ReplicaSetConfigurer {
        private static final ReplicaSetConfigurer instance = new ReplicaSetConfigurer();

        private final AtomicBoolean isStarted = new AtomicBoolean(false);

        private Map<MongodProcess, MongodExecutable> mongodProcessMap;
        private MongoClient mongoClient;
        private List<Integer> ports;
        private MongoDatabase adminDatabase;

        private ReplicaSetConfigurer() {
            mongodProcessMap = new ConcurrentHashMap<>();
            ports = new CopyOnWriteArrayList<>();
        }

        public synchronized void start(String mongoDbConnectionString) throws IOException {
            if (isStarted.get()) return;
            isStarted.set(true);

            ConnectionString connectionString = new ConnectionString(mongoDbConnectionString);
            List<String> replicaHosts = connectionString.getHosts();
            String dbName = connectionString.getDatabase();
            String replicaName = connectionString.getRequiredReplicaSetName();

            MongodStarter runtime = MongodStarter.getDefaultInstance();
            for (int i=0; i<replicaHosts.size(); i++) {
                String host = replicaHosts.get(i);
                Assert.assertNotNull(host);
                Assert.assertTrue(host.contains(":"));

                String[] hostInfo = host.split(":");
                Assert.assertEquals(2, hostInfo.length);
                int port = Integer.parseInt(hostInfo[1]);

                MongodExecutable mongodExecutable = runtime.prepare(new MongodConfigBuilder().version(Version.Main.V4_0)
                        .withLaunchArgument("--replSet", replicaName)
                        .cmdOptions(new MongoCmdOptionsBuilder().useNoJournal(false).build())
                        .net(new Net(port, Network.localhostIsIPv6())).build());
                MongodProcess mongoProcess = mongodExecutable.start();

                mongodProcessMap.put(mongoProcess, mongodExecutable);
                ports.add(port);
            }

            mongoClient = new MongoClient(new ServerAddress(Network.getLocalHost(), ports.get(0)));
            adminDatabase = mongoClient.getDatabase("admin");

            Document config = new Document("_id", replicaName);
            BasicDBList members = new BasicDBList();
            for (int i=0; i<replicaHosts.size(); i++) {
                members.add(new Document("_id", i)
                        .append("host", replicaHosts.get(i))
                        .append("priority", i==0 ? 1:0.5)
                );
            }
            config.put("members", members);

            System.out.println(">>>>>>>> rs.initiate()");
            adminDatabase.runCommand(new Document("replSetInitiate", config));

            System.out.println(">>>>>>>> rs.status()");
            System.out.println(adminDatabase.runCommand(new Document("replSetGetStatus", 1)));

            try {
                /**
                 * The median time before a cluster elects a new primary should not typically exceed 12 seconds,
                 * assuming default replica configuration settings. This includes time required to mark the primary
                 * as unavailable and call and complete an election.
                 */
                Thread.sleep(15000);
            } catch (InterruptedException e) {}

            System.out.println(">>>>>>>> isMaster()");
            System.out.println(adminDatabase.runCommand(new Document("isMaster", 1)));

            // Adding shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::finish));
        }

        public synchronized void finish() {
            if (!isStarted.get()) return;
            isStarted.set(false);

            System.out.println(">>>>>> shutting down");

            if (mongoClient != null) {
                mongoClient.close();
            }

            mongodProcessMap.entrySet().forEach(entry -> {
                MongodExecutable mongodExecutable = entry.getValue();
                if (mongodExecutable != null) {
                    mongodExecutable.stop();
                }
                MongodProcess mongodProcess = entry.getKey();
                if (mongodProcess != null) {
                    mongodProcess.stop();
                }
            });

            mongodProcessMap.clear();
            ports.clear();
        }
    }
}
