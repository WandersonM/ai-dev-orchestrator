package com.ordevia.aidev.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.audit.domain.AuditEvent;
import com.ordevia.aidev.audit.infrastructure.AuditEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventJpaRepository events;
    private final ObjectMapper mapper;

    public AuditService(AuditEventJpaRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent append(UUID workItemId, UUID sessionId, String eventType,
                             String actorType, String actorId,
                             String entityType, String entityId,
                             Map<String, ?> payload) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), workItemId, sessionId, eventType,
                actorType, actorId, entityType, entityId, json(payload));
        return events.save(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> byWorkItem(UUID workItemId) {
        return events.findByWorkItemIdOrderByCreatedAtAsc(workItemId);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> bySession(UUID sessionId) {
        return events.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private String json(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize audit event payload", e);
        }
    }
}
