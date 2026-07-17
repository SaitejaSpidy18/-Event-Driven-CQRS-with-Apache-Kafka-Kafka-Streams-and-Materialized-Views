package com.example.command.event;

import java.time.Instant;
import java.util.UUID;

public class EventEnvelope<T> {
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private String schemaVersion = "v1";
    private Instant timestamp = Instant.now();
    private T payload;

    public EventEnvelope() {}

    public EventEnvelope(String eventType, T payload) {
        this.eventType = eventType;
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}
