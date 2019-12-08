package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.config.MongoDbConfig;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.util.ObjectMapperFactory;
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
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.*;
import static com.mongodb.client.model.Updates.unset;

@Component
public class MongoEventStoreDao extends AbstractEventStoreDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoEventStoreDao.class);

    @Value("${eventstore.retry.message.expired.seconds:15}")
    private long messageExpiredTimeInSec;

    @Autowired
    private MongoEventStoreRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoDbConfig mongoDbConfig;

    @Override
    public DomainEvent createEvent(String eventId, String header, String payload, String payloadClass) {
        DomainEvent domainEvent = new DomainEvent(
                eventId,
                header,
                payload,
                payloadClass,
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
//                            Map headers = ObjectMapperFactory.getMapper().fromJson(document.get("header", String.class), Map.class);
//                            headers.put("eventId", eventId);
//                            String payload = ObjectMapperFactory.getMapper().fromJson(document.get("payload", String.class), String.class);
//
//                            Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
//                            LOGGER.debug("send message: {}", message);
//                            sendMessage(message);

                            callbackCommand.pendingEventfetched(
                                    eventId,
                                    document.get("header", String.class),
                                    document.get("payload", String.class),
                                    Object.class
                            );

                        } catch (IOException e) {
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

//    public void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand) {
//        try {
//            callbackCommand.pendingEventfetched("123456", "{}", "{}", Object.class);
//        } catch (IOException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//    }

    @Override
    public void deleteAll() {
        mongoRepository.deleteAll();
    }

    @Override
    public List<DomainEvent> findAll() {
        return mongoRepository.findAll();
    }
}
