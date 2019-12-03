package com.jeffrey.example.demoapp.service;

import com.jeffrey.example.demoapp.entity.DomainEvent;
import com.jeffrey.example.demoapp.repository.EventStoreRepository;
import com.jeffrey.example.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
}
