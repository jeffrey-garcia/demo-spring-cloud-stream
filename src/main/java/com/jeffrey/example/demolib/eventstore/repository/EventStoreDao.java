package com.jeffrey.example.demolib.eventstore.repository;


import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

public interface EventStoreDao {

    void initializeDb(Collection<String> outputChannelNames);

    void configureClock(Clock clock);

    DomainEvent createEvent(String eventId, String header, String payload, String payloadClass, String outputChannelName);

    DomainEvent updateReturnedTimestamp(String eventId, String outputChannelName);

    DomainEvent updateProducedTimestamp(String eventId, String outputChannelName);

    boolean hasConsumedTimeStamp(String eventId, String outputChannelName);

    DomainEvent updateConsumedTimestamp(String eventId, String outputChannelName);

    void filterPendingProducerAckOrReturned(String outputChannelName, EventStoreCallbackCommand callbackCommand);

    void deleteAll(String outputChannelName);

    List<DomainEvent> findAll(String outputChannelName);

}
