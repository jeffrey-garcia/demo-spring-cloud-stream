package com.jeffrey.example.demoapp.repository;

import com.jeffrey.example.demoapp.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.entity.DomainEvent;

import java.util.List;

public interface EventStoreDao {

    DomainEvent createEvent(String eventId, String header, String payload, String payloadClass);

    DomainEvent updateReturnedTimestamp(String eventId);

    DomainEvent updateProducedTimestamp(String eventId);

    DomainEvent updateConsumedTimestamp(String eventId);

//    void filterPendingProducerAckOrReturned();

    void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand);

    void deleteAll();

    List<DomainEvent> findAll();

}
