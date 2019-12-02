package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.entity.DomainEvent;
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

    private MongoEventStoreRepository mongoRepository;
    private MongoTemplate mongoTemplate;

    public EventStoreRepository(
            @Autowired MongoEventStoreRepository mongoRepository,
            @Autowired MongoTemplate mongoTemplate)
    {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public DomainEvent createEvent(String header, String payload) {
        DomainEvent domainEvent = new DomainEvent(
                header,
                payload,
                LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
        );
        return mongoRepository.save(domainEvent);
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