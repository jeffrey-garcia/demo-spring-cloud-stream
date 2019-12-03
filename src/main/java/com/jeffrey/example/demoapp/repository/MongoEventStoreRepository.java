package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface MongoEventStoreRepository extends MongoRepository<DomainEvent, String> {

    @Query("{ 'producerAckOn' : null }")
    List<DomainEvent> findAllByProducerAckOnIsNull();

}
