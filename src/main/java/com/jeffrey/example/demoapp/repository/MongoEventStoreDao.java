package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.config.MongoDbConfig;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
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
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Component
public class MongoEventStoreDao extends AbstractEventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoEventStoreDao.class);

    @Value("${eventstore.retry.message.expired.seconds:60}") // message sending expiry default to 60s
    private long messageExpiredTimeInSec;

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

    @Override
    public void initializeDb() {
        // Although index creation via annotations comes in handy for many scenarios
        // consider taking over more control by setting up indices manually via IndexOperations.
        IndexOperations indexOps = mongoTemplate.indexOps(DomainEvent.class);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mongoMappingContext);
        resolver.resolveIndexFor(DomainEvent.class).forEach(indexOps::ensureIndex);
    }

    @Override
    public void configureClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public DomainEvent createEvent(String eventId, String header, String payload, String payloadClassName) {
        DomainEvent domainEvent = new DomainEvent(
                eventId,
                header,
                payload,
                payloadClassName,
                ZonedDateTime.now(clock).toInstant()
        );
        return mongoRepository.save(domainEvent);
    }

    @Override
    public DomainEvent updateReturnedTimestamp(String eventId) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("returnedOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class);
    }

    @Override
    public DomainEvent updateProducedTimestamp(String eventId) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("producerAckOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class);
    }

    @Override
    public DomainEvent updateConsumedTimestamp(String eventId) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("consumerAckOn", ZonedDateTime.now(clock).toInstant());
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class);
    }

    @Override
    public void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand) {
        LOGGER.debug("filter pending event operation");

        MongoClient client = mongoDbConfig.mongoClient();
        String dbName = mongoDbConfig.mongoDbFactory().getDb().getName();

        try (ClientSession session = client.startSession()) {
            session.withTransaction(() -> {
                MongoCollection<Document> collection = client.getDatabase(dbName).getCollection("DemoEventStoreV2");

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
                FindIterable<Document> documents = collection.find(
                        session,
                        combine(
                                lt("writtenOn", currentDateTime.minusSeconds(messageExpiredTimeInSec)),
                                or(eq("producerAckOn", null),ne("returnedOn", null)),
                                eq("consumerAckOn", null)
                        )
                );

                for (Document document:documents) {
                    String eventId = document.get("_id", String.class);
                    UpdateResult result = collection.updateOne(
                            session,
                            eq("_id", eventId),
                            combine(
                                    inc("attemptCount", +1L),
                                    set("writtenOn", ZonedDateTime.now(clock).toInstant()),
                                    unset("producerAckOn"), // remove the producer ack timestamp upon resend
                                    unset("returnedOn") // remove the return timestamp upon resend
                            )
                    );

                    if (result.getModifiedCount() == 1) {
                        LOGGER.debug("retry callback event id: {}", eventId);
                        try {
                            callbackCommand.pendingEventFetched(
                                    eventId,
                                    document.get("header", String.class),
                                    document.get("payload", String.class),
                                    Class.forName(document.get("payloadClassName", String.class))
                            );
                        } catch (Exception e) {
                            // one event fail shouldn't cancel the entire retry operation
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
                }

                return null;
            });

        } catch (RuntimeException e) {
            LOGGER.error("abort tx: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void deleteAll() {
        mongoRepository.deleteAll();
    }

    @Override
    public List<DomainEvent> findAll() {
        return mongoRepository.findAll();
    }
}
