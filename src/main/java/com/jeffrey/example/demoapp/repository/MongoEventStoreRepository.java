package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoEventStoreRepository extends MongoRepository<DomainEvent, String> {
}
