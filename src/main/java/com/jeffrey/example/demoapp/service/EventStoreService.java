package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
import com.jeffrey.example.util.ObjectMapperFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Service
public class EventStoreService {

    @Autowired
    EventStoreRepository eventStoreRepository;

    public DomainEvent createEvent(Message<?> message) throws IOException {
        String jsonHeader = ObjectMapperFactory.getMapper().toJson(message.getHeaders());
        String jsonPayload = ObjectMapperFactory.getMapper().toJson(message.getPayload());
        return eventStoreRepository.createEvent(jsonHeader, jsonPayload);
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

}
