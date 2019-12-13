package com.jeffrey.example.demolib.eventstore.repository;

import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demoapp.entity.DomainEvent;

import java.time.Clock;

public abstract class AbstractEventStoreDao implements EventStoreDao {

    public abstract void initializeDb();

    public abstract void configureClock(Clock clock);

    public abstract DomainEvent createEvent(
            String eventId, String header, String payload, String payloadClassName, String outputChannelName);

    public abstract DomainEvent updateReturnedTimestamp(String eventId);

    public abstract DomainEvent updateProducedTimestamp(String eventId);

    public abstract DomainEvent updateConsumedTimestamp(String eventId);

    public abstract void filterPendingProducerAckOrReturned(EventStoreCallbackCommand callbackCommand);

}
