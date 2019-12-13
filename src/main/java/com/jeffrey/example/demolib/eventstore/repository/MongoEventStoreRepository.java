package com.jeffrey.example.demolib.eventstore.repository;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

public interface MongoEventStoreRepository extends MongoRepository<DomainEvent, String> {

}
