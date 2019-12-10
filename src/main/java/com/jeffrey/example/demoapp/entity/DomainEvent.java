package com.jeffrey.example.demoapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.hash.Hashing;
import com.jeffrey.example.demoapp.util.ObjectMapperFactory;
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

    @JsonProperty("header")
    private String header;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("payloadClassName")
    private String payloadClassName;

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

    public DomainEvent(String id, String header, String payload, String payloadClassName, Instant writtenOn) {
        this.id = id;
        this.header = header;
        this.payload = payload;
        this.payloadClassName = payloadClassName;
        this.writtenOn = writtenOn;
        this.attemptCount = 1L;
    }

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

    public String getPayloadClassName() {
        return payloadClassName;
    }
}
