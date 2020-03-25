package com.jeffrey.example.demolib.eventstore.dao;

import com.jeffrey.example.demolib.eventstore.command.EventStoreCallbackCommand;
import com.jeffrey.example.demolib.eventstore.entity.DomainEvent;

import java.time.Clock;
import java.util.Collection;

/**
 * Base class for {@link MongoEventStoreDao} implementations providing common
 * functionality of the event store.
 *
 * @author Jeffrey Garcia Wong
 */
public abstract class AbstractEventStoreDao implements EventStoreDao {

    public abstract void initializeDb(Collection<String> outputChannelNames);

    public abstract void configureClock(Clock clock);

    public abstract DomainEvent createEvent(
            String eventId, String header, String payload, String payloadClassName, String outputChannelName);

    public abstract DomainEvent updateReturnedTimestamp(String eventId, String outputChannelName);

    public abstract DomainEvent updateProducedTimestamp(String eventId, String outputChannelName);

    public abstract boolean hasConsumedTimeStamp(String eventId, String outputChannelName);

    public abstract DomainEvent updateConsumedTimestamp(String eventId, String outputChannelName);

    public abstract void filterPendingProducerAckOrReturned(String outputChannelName, EventStoreCallbackCommand callbackCommand);

}
