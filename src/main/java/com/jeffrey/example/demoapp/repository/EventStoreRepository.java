package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Component
public class EventStoreRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreRepository.class);

    @Autowired
    private MongoEventStoreRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public DomainEvent createEvent(String eventId, String header, String payload) {
        DomainEvent domainEvent = new DomainEvent(
                eventId,
                header,
                payload,
                LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        );
        return mongoRepository.save(domainEvent);
    }

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

    public void deleteAll() {
        mongoRepository.deleteAll();
    }

    public List<DomainEvent> findAll() {
        return mongoRepository.findAll();
    }
}
