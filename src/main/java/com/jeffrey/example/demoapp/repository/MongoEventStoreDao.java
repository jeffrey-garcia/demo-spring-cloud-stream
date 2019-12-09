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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Component
public class MongoEventStoreDao extends AbstractEventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoEventStoreDao.class);

    @Value("${eventstore.retry.message.expired.seconds:60}") // message sending expiry default to 60s
    private long messageExpiredTimeInSec;

    @Autowired
    private MongoEventStoreRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoDbConfig mongoDbConfig;

    @Override
    public DomainEvent createEvent(String eventId, String header, String payload, String payloadClassName) {
        DomainEvent domainEvent = new DomainEvent(
                eventId,
                header,
                payload,
                payloadClassName,
                LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        );
        return mongoRepository.save(domainEvent);
    }

    @Override
    public DomainEvent updateReturnedTimestamp(String eventId) {
        // atomically query and update the document
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(eventId));
        Update update = new Update();
        update.set("returnedOn", LocalDateTime.ofInstant(Instant.now(), ZoneOffset.systemDefault()));
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
        update.set("producerAckOn", LocalDateTime.ofInstant(Instant.now(), ZoneOffset.systemDefault()));
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
        update.set("consumerAckOn", LocalDateTime.ofInstant(Instant.now(), ZoneOffset.systemDefault()));
        return mongoTemplate.findAndModify(
                query,
                update,
                new FindAndModifyOptions().returnNew(true),
                DomainEvent.class);
    }

    @Override
    public void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand) {
        MongoClient client = mongoDbConfig.mongoClient();
        String dbName = mongoDbConfig.mongoDbFactory().getDb().getName();

        try (ClientSession session = client.startSession()) {
            session.withTransaction(() -> {
                MongoCollection<Document> collection = client.getDatabase(dbName).getCollection("DemoEventStoreV2");

                // query all message that was sent at least X (messageExpiredTimeInSec) seconds ago
                // X should NOT be less than the typical message sending timeout
                LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
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
                                    set("writtenOn", LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())),
                                    unset("producerAckOn"), // remove the producer ack timestamp upon resend
                                    unset("returnedOn") // remove the return timestamp upon resend
                            )
                    );

                    if (result.getModifiedCount() == 1) {
                        LOGGER.debug("retry event id: {}", eventId);

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
