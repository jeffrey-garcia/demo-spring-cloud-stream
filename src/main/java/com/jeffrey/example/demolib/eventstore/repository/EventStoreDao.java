package com.jeffrey.example.demolib.eventstore.repository;

import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;

import java.time.Clock;
import java.util.List;

public interface EventStoreDao {

    void initializeDb();

    void configureClock(Clock clock);

    DomainEvent createEvent(String eventId, String header, String payload, String payloadClass, String outputChannelName);

    DomainEvent updateReturnedTimestamp(String eventId);

    DomainEvent updateProducedTimestamp(String eventId);

    boolean hasConsumedTimeStamp(String eventId);

    DomainEvent updateConsumedTimestamp(String eventId);

    void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand);

    void deleteAll();

    List<DomainEvent> findAll();

}
