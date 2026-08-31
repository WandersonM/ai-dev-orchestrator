package com.ordevia.aidev.audit.infrastructure;

import com.ordevia.aidev.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
    List<AuditEvent> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
