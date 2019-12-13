package com.jeffrey.example.demolib.eventstore.repository;

import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoEventStoreRepository extends MongoRepository<DomainEvent, String> {

}
