package com.ordevia.aidev.audit.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="audit_event")
public class AuditEvent {
    @Id private UUID id;
    @Column(name="work_item_id") private UUID workItemId;
    @Column(name="session_id") private UUID sessionId;
    @Column(name="event_type",nullable=false,length=120) private String eventType;
    @Column(name="actor_type",nullable=false,length=40) private String actorType;
    @Column(name="actor_id",length=200) private String actorId;
    @Column(name="entity_type",nullable=false,length=120) private String entityType;
    @Column(name="entity_id",length=200) private String entityId;
    @Column(name="payload_json",columnDefinition="text") private String payloadJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected AuditEvent() {}
    public AuditEvent(UUID id,UUID workItemId,UUID sessionId,String eventType,String actorType,String actorId,String entityType,String entityId,String payloadJson){
        this.id=id;this.workItemId=workItemId;this.sessionId=sessionId;this.eventType=eventType;this.actorType=actorType;this.actorId=actorId;
        this.entityType=entityType;this.entityId=entityId;this.payloadJson=payloadJson;this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public UUID getSessionId(){return sessionId;} public String getEventType(){return eventType;}
    public String getActorType(){return actorType;} public String getActorId(){return actorId;} public String getEntityType(){return entityType;} public String getEntityId(){return entityId;}
    public String getPayloadJson(){return payloadJson;} public Instant getCreatedAt(){return createdAt;}
}
