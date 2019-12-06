package com.jeffrey.example.demoapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.Hashing;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.HashIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "DemoEventStoreV2") // specify the name of the collection in MongoDB
public class DomainEvent {

    // Hashed indexes allow hash based sharding to partition data across sharded cluster.
    // Using hashed field values to shard collections results in a more random distribution.
    // Since ObjectId increases monotonically
    @HashIndexed
    @Id
    @JsonProperty("id")
    private String id;

    @JsonProperty("header")
    private String header;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("writtenOn")
    private LocalDateTime writtenOn;

    @JsonProperty("attemptCount")
    private long attemptCount;

    @JsonProperty("returnedOn")
    private LocalDateTime returnedOn;

    @JsonProperty("producerAckOn")
    private LocalDateTime producerAckOn;

    @JsonProperty("consumerAckOn")
    private LocalDateTime consumerAckOn;

    public DomainEvent(String id, String header, String payload, LocalDateTime writtenOn) {
        this.id = id;
        this.header = header;
        this.payload = payload;
        this.writtenOn = writtenOn;
    }

    @Override
    public int hashCode() {
        return Hashing.sha256().hashBytes(id.getBytes()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof DomainEvent && obj.hashCode() == this.hashCode();
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

    public LocalDateTime getWrittenOn() {
        return writtenOn;
    }

    public long getAttemptCount() { return attemptCount; }

    public LocalDateTime getReturnedOn() {
        return returnedOn;
    }

    public LocalDateTime getProducerAckOn() {
        return producerAckOn;
    }

    public LocalDateTime getConsumerAckOn() {
        return consumerAckOn;
    }
}
