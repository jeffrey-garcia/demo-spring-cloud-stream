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
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

@Service
public class EventStoreService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

    @Value("${eventstore.retry.message.expired.seconds:15}")
    private long messageExpiredTimeInSec;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DemoMongoDbConfig dbConfig;

    @Autowired
    private EventStoreRepository eventStoreRepository;

    public Message<?> createEventFromMessage(Message<?> message) throws IOException {
        String eventId = UUID.randomUUID().toString();

        MessageHeaderAccessor accessor = MessageHeaderAccessor.getMutableAccessor(message);
        accessor.setHeader("eventId", eventId);
        MessageHeaders messageHeaders = accessor.getMessageHeaders();
        message = MessageBuilder.fromMessage(message).copyHeaders(messageHeaders).build();

        String jsonHeader = ObjectMapperFactory.getMapper().toJson(message.getHeaders());
        String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
        eventStoreRepository.createEvent(eventId, jsonHeader, jsonPayload);

        return message;
    }

    public DomainEvent updateEventAsReturned(String eventId) throws NullPointerException {
        if (StringUtils.isEmpty(eventId)) {
            throw new NullPointerException("eventId should not be null");
        }
        return eventStoreRepository.updateReturnedTimestamp(eventId);
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

    public void retryOperation() {
        MongoClient client = dbConfig.mongoClient();
        String dbName = dbConfig.mongoDbFactory().getDb().getName();

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
                            Map headers = ObjectMapperFactory.getMapper().fromJson(document.get("header", String.class), Map.class);
                            headers.put("eventId", eventId);
                            String payload = ObjectMapperFactory.getMapper().fromJson(document.get("payload", String.class), String.class);

                            Message message = MessageBuilder.withPayload(payload).copyHeaders(headers).build();
                            LOGGER.debug("send message: {}", message);
                            sendMessage(message);

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

    protected void sendMessage(Message<?> message) {
        String[] bindableBeanNames = applicationContext.getBeanNamesForType(Bindable.class);
        LOGGER.debug("bindable beans: {}", bindableBeanNames);

        for (String bindableBeanName:bindableBeanNames) {
            Bindable bindable = (Bindable) applicationContext.getBean(bindableBeanName);
            for (String binding:bindable.getOutputs()) {
                Object bindableBean = applicationContext.getBean(binding);
                if (bindableBean instanceof AbstractMessageChannel) {
                    AbstractMessageChannel abstractMessageChannel = (AbstractMessageChannel) bindableBean;
                    LOGGER.debug("sending message to output message channel: {}", abstractMessageChannel.getFullChannelName());
                    abstractMessageChannel.send(message);
                }
            }
        }
    }

}
