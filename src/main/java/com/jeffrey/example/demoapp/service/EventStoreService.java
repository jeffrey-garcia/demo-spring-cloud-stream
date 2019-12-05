package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.config.DemoMongoDbConfig;
import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
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
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Updates.*;

@Service
public class EventStoreService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Autowired
    EventStoreRepository eventStoreRepository;

    public DomainEvent upsertEvent(Message<?> message) throws IOException {
        Map<String, Object> headers = new HashMap<>(message.getHeaders().size());
        message.getHeaders().forEach(headers::put);
        String eventId = message.getHeaders().get("eventId", String.class);
        if (StringUtils.isEmpty(eventId)) {
            eventId = UUID.randomUUID().toString();
            headers.put("eventId", eventId);
            String jsonHeader = ObjectMapperFactory.getMapper().toJson(headers);
            String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
            return eventStoreRepository.createEvent(eventId, jsonHeader, jsonPayload);
        } else {
            String jsonHeader = ObjectMapperFactory.getMapper().toJson(headers);
            String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
            return eventStoreRepository.updateWrittenTimestamp(eventId, jsonHeader, jsonPayload);
        }
    }

    public DomainEvent updateEventAsProduced(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreRepository.updateProducedTimestamp(eventId);
    }

    public DomainEvent updateEventAsConsumed(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreRepository.updateConsumedTimestamp(eventId);
    }

    public List<DomainEvent> findAllPendingProducerAckEvents() {
        return eventStoreRepository.findAllPendingProducerAck();
    }

    @Value("${eventstore.producer.timeout.seconds:15}")
    private long producerTimeoutInSec;

    @Autowired
    Source source;

    @Autowired
    DemoMongoDbConfig dbConfig;

    public void retryOperation() {
        MongoClient client = dbConfig.mongoClient();
        String dbName = dbConfig.mongoDbFactory().getDb().getName();

        try (ClientSession session = client.startSession()) {
            session.withTransaction(() -> {
                MongoCollection<Document> collection = client.getDatabase(dbName).getCollection("DemoEventStoreV2");

                LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
                FindIterable<Document> documents = collection.find(
                        session,
                        combine(
                            lt("writtenOn", currentDateTime.minusSeconds(producerTimeoutInSec)),
                            eq("producerAckOn", null),
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
                            set("writtenOn", LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()))
                        )
                    );

                    if (result.getModifiedCount() == 1) {
                        LOGGER.debug("updated event id: {}", eventId);

                        try {
                            Map headers = ObjectMapperFactory.getMapper().fromJson(document.get("header", String.class), Map.class);
                            headers.put("eventId", eventId);
                            String payload = ObjectMapperFactory.getMapper().fromJson(document.get("payload", String.class), String.class);

                            Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
                            LOGGER.debug("send message: {}", message);
                            source.output().send(message);

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

}
