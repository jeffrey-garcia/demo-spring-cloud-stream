package com.jeffrey.example.demolib.eventstore.dao;

import com.google.common.collect.Iterables;
import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demolib.eventstore.config.MongoDbConfig;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;
import com.jeffrey.example.demolib.eventstore.repository.MongoEventStoreRepository;
import com.mongodb.BasicDBObject;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Component("MongoEventStoreDao")
@EnableMongoRepositories(basePackageClasses = MongoEventStoreRepository.class)
public class MongoEventStoreDao extends AbstractEventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoEventStoreDao.class);

    @Value("${com.jeffrey.example.eventstore.retry.message.expired.seconds:60}") // message sending expiry default to 60s
    private long messageExpiredTimeInSec;

    @Value("${com.jeffrey.example.eventstore.mongo.collectionPrefix:DefaultEventStore}") // use default collection prefix if not defined
    private String eventStorePrefix;

    @Value("${com.jeffrey.example.eventstore.retry.message.batchSize:1000}") // default message count per retry to 1000
    private int retryMessageBatchSize;

    @Value("${com.jeffrey.example.eventstore.consumer.expiredTimeInSec:0}") // default to 0s if not defined
    private int messageConsumerExpiryTimeInSec;

    private Clock clock;

    private MongoDbConfig mongoDbConfig;

    private MongoEventStoreRepository mongoRepository;

    private MongoTemplate mongoTemplate;

    private MongoMappingContext mongoMappingContext;

    public MongoEventStoreDao(
            @Autowired @Qualifier("eventStoreClock") Clock clock,
            @Autowired MongoDbConfig mongoDbConfig,
            @Autowired MongoEventStoreRepository mongoRepository,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired MongoMappingContext mongoMappingContext
    ) {
        this.clock = clock;
        this.mongoDbConfig = mongoDbConfig;
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.mongoMappingContext = mongoMappingContext;
    }

    private String getStoreName(String outputChannelName) {
        return String.format("%s-%s", eventStorePrefix, outputChannelName);
    }

    @Override
    public void initializeDb(Collection<String> outputChannelNames) {
        // create the event store collections if not exist
        for (String outputChannelName:outputChannelNames) {
            String eventStoreName = getStoreName(outputChannelName);
            if (!mongoTemplate.collectionExists(eventStoreName)) {
                mongoTemplate.createCollection(eventStoreName);
            }

            // Although index creation via annotations comes in handy for many scenarios
            // consider taking over more control by setting up indices manually via IndexOperations.
            IndexOperations indexOps = mongoTemplate.indexOps(eventStoreName);
            IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
            resolver.resolveIndexFor(DomainEvent.class).forEach(indexOps::ensureIndex);
        }
    }

    @Override
    public void configureClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public DomainEvent createEvent(
            String eventId, String header, String payload, String payloadClassName, String outputChannelName)
    {
        DomainEvent domainEvent = new DomainEvent.Builder()
                .id(eventId)
                .channel(outputChannelName)
                .header(header)
                .payload(payload)
                .payloadType(payloadClassName)
                .writtenOn(ZonedDateTime.now(clock).toInstant())
                .build();

        return mongoTemplate.save(domainEvent, getStoreName(outputChannelName));
    }

    @Override
    public DomainEvent updateReturnedTimestamp(String eventId, String outputChannelName) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("returnedOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class,
                getStoreName(outputChannelName)
        );
    }

    @Override
    public DomainEvent updateProducedTimestamp(String eventId, String outputChannelName) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("producerAckOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class,
                getStoreName(outputChannelName)
        );
    }

    @Override
    public boolean hasConsumedTimeStamp(String eventId, String outputChannelName) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        query.addCriteria(Criteria.where("consumerAckOn").ne(null));
        return mongoTemplate.exists(
                query,
                DomainEvent.class,
                getStoreName(outputChannelName)
        );
    }

    @Override
    public DomainEvent updateConsumedTimestamp(String eventId, String outputChannelName) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("consumerAckOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class,
                getStoreName(outputChannelName)
        );
    }

    @Override
    public void filterPendingProducerAckOrReturned(String outputChannelName, EventStoreCallbackCommand callbackCommand) {
        LOGGER.debug("filter pending event operation");

        MongoClient client = mongoDbConfig.mongoClient();
        String dbName = mongoDbConfig.mongoDbFactory().getDb().getName();

        try (ClientSession session = client.startSession()) {
            /**
             * Runs a provided lambda within a transaction, auto-retrying either the commit operation
             * or entire transaction as needed (and when the error permits) to better ensure that
             * the transaction can complete successfully.
             *
             * To coordinate read and write operations with a transaction, you must pass the session
             * to each operation in the transaction (transactions are associated with a session.)
             * At any given time, at most one open transaction for a session.
             *
             * Requires at least:
             * - MongoDB 4.0 with Replica Sets
             * - Spring-Data-Mongo 2.2
             * - MongoDB Java Driver 3.8
             */
            session.withTransaction(() -> {
                long retrySuccessfulCount = 0L;
                MongoCollection<Document> collection = client
                                                        .getDatabase(dbName)
                                                        .getCollection(getStoreName(outputChannelName));

                /**
                 * query all message that was sent at least X (messageExpiredTimeInSec) seconds ago
                 * X should NOT be less than the typical message sending timeout
                 */
                Instant currentDateTime = ZonedDateTime.now(clock).toInstant();

                /**
                 * If queries do not include the shard key or the prefix of a compound shard key,
                 * mongos performs a broadcast operation, querying all shards in the sharded cluster.
                 * These scatter/gather queries can be long running operations.
                 */
                Bson bson;
                if (messageConsumerExpiryTimeInSec>0) {
                    // an additional query routine to fetch message that has not been consumed after a prolonged period of time
                    // for example, a returned message whose returned timestamp maybe failed to record in the event store
                    bson = combine(
                                lt("writtenOn", currentDateTime.minusSeconds(messageExpiredTimeInSec)),
                                or(
                                        lt("producerAckOn", currentDateTime.minusSeconds(messageConsumerExpiryTimeInSec)),
                                        eq("producerAckOn", null),
                                        ne("returnedOn", null)
                                ),
                                eq("consumerAckOn", null)
                            );
                } else {
                    bson = combine(
                                lt("writtenOn", currentDateTime.minusSeconds(messageExpiredTimeInSec)),
                                or(eq("producerAckOn", null),ne("returnedOn", null)),
                                eq("consumerAckOn", null)
                            );
                }

                FindIterable<Document> documents = collection.find(session, bson)
                // sort the results based on writtenOn timestamp in ascending order
                .sort(new BasicDBObject("writtenOn", -1))
                // limit the result set to avoid overwhelming the broker
                .limit(retryMessageBatchSize);

                LOGGER.debug("total no. of events eligible for retry: {}", Iterables.size(documents));

                for (Document document:documents) {
                    final String eventId = document.get("_id", String.class);
                    final String outputChannelBeanName = document.get("channel", String.class);
                    final String header = document.get("header", String.class);
                    final String payload = document.get("payload", String.class);
                    final String payloadClassName = document.get("payloadType", String.class);

                    final Instant writtenOn = ZonedDateTime.now(clock).toInstant();

                    UpdateResult result = collection.updateOne(
                            session,
                            eq("_id", eventId),
                            combine(
                                    inc("attemptCount", +1L),
                                    set("writtenOn", writtenOn),
                                    unset("producerAckOn"), // remove the producer ack timestamp upon resend
                                    unset("returnedOn") // remove the return timestamp upon resend
                            )
                    );

                    if (result.getModifiedCount() == 1) {
                        LOGGER.debug("retry callback event id: {}", eventId);

                        DomainEvent domainEvent = new DomainEvent.Builder()
                                .id(eventId)
                                .channel(outputChannelBeanName)
                                .header(header)
                                .payload(payload)
                                .payloadType(payloadClassName)
                                .writtenOn(writtenOn)
                                .build();

                        try {
                            callbackCommand.pendingEventFetched(domainEvent);
                            retrySuccessfulCount += result.getModifiedCount();

                        } catch (Exception e) {
                            // one event fail shouldn't affect the entire retry operation
                            LOGGER.warn("error while sending event: {} {}", eventId, e.getMessage());
                        }
                    }
                }

                LOGGER.debug("total no. of successful retry: {}", retrySuccessfulCount);
                return retrySuccessfulCount;
            });

        } catch (RuntimeException e) {
            LOGGER.error("error processing pending event: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteAll(String outputChannelName) {
        if (mongoTemplate.collectionExists(getStoreName(outputChannelName))) {
            mongoTemplate.dropCollection(getStoreName(outputChannelName));
        }
    }

    @Override
    public List<DomainEvent> findAll(String outputChannelName) {
        return mongoTemplate.findAll(DomainEvent.class, getStoreName(outputChannelName));
    }
}
