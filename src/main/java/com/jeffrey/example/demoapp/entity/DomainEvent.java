package com.jeffrey.example.demoapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.hash.Hashing;
import com.jeffrey.example.demolib.eventstore.util.ObjectMapperFactory;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.HashIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "DemoEventStoreV2") // specify the name of the collection in MongoDB
public class DomainEvent {

    /**
     * The shard key to distribute the collection’s documents across shards.
     *
     * Hashed Sharding involves computing a hash of the shard key field’s value.
     * Each chunk is then assigned a range based on the hashed shard key values.
     *
     * Hashed indexes allow hash based sharding to partition data across sharded cluster.
     * Using hashed field values to shard collections results in a more random distribution
     * since MongoDB's ObjectId increases monotonically.
     *
     * However, hashed distribution means that ranged-based queries on the shard key are
     * less likely to target a single shard, resulting in more cluster wide broadcast
     * operations
     *
     * TODO: Isolate a specific subset of data on a specific set of shards using zone
     */
    @HashIndexed
    @Id
    @JsonProperty("id")
    private String id;

    @JsonProperty("channel")
    private String channel;


    @JsonProperty("header")
    private String header;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("payloadType")
    private String payloadType;

    @JsonProperty("writtenOn")
    private Instant writtenOn;

    @JsonProperty("attemptCount")
    private long attemptCount;

    @JsonProperty("returnedOn")
    private Instant returnedOn;

    @JsonProperty("producerAckOn")
    private Instant producerAckOn;

    @JsonProperty("consumerAckOn")
    private Instant consumerAckOn;

    private DomainEvent() {}

    @Override
    public int hashCode() {
        return Hashing.sha256().hashBytes(id.getBytes()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this && obj != null && obj.getClass() == this.getClass() && this.toString().equals(obj.toString());
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory.getMapper().toJson(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getId() {
        return id;
    }

    public String getChannel() { return channel; }

    public String getHeader() {
        return header;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getWrittenOn() {
        return writtenOn;
    }

    public long getAttemptCount() { return attemptCount; }

    public Instant getReturnedOn() {
        return returnedOn;
    }

    public Instant getProducerAckOn() {
        return producerAckOn;
    }

    public Instant getConsumerAckOn() {
        return consumerAckOn;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public static class Builder {
        private String id;
        private String channel;
        private String header;
        private String payload;
        private String payloadType;
        private Instant writtenOn;
        private long attemptCount = 1L; // default
        private Instant returnedOn;
        private Instant producerAckOn;
        private Instant consumerAckOn;

        public Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder header(String header) {
            this.header = header;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder payloadType(String payloadType) {
            this.payloadType = payloadType;
            return this;
        }

        public Builder writtenOn(Instant writtenOn) {
            this.writtenOn = writtenOn;
            return this;
        }

        public Builder attemptCount(long attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public Builder returnedOn(Instant returnedOn) {
            this.returnedOn = returnedOn;
            return this;
        }

        public Builder producerAckOn(Instant producerAckOn) {
            this.producerAckOn = producerAckOn;
            return this;
        }

        public Builder consumerAckOn(Instant consumerAckOn) {
            this.consumerAckOn = consumerAckOn;
            return this;
        }

        public DomainEvent build() {
            DomainEvent domainEvent = new DomainEvent();
            domainEvent.id = this.id;
            domainEvent.channel = this.channel;
            domainEvent.header = this.header;
            domainEvent.payload = this.payload;
            domainEvent.payloadType = this.payloadType;
            domainEvent.writtenOn = this.writtenOn;
            domainEvent.attemptCount = this.attemptCount;
            domainEvent.returnedOn = this.returnedOn;
            domainEvent.producerAckOn = this.producerAckOn;
            domainEvent.consumerAckOn = this.consumerAckOn;
            return domainEvent;
        }
    }

}
