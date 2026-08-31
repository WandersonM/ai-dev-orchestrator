package com.ordevia.aidev.session.infrastructure;

import com.ordevia.aidev.session.domain.AgentSessionMessage;
import com.ordevia.aidev.session.domain.AgentSessionMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentSessionMessageJpaRepository extends JpaRepository<AgentSessionMessage, UUID> {
    List<AgentSessionMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    List<AgentSessionMessage> findBySessionIdAndRoleAndConsumedAtIsNullOrderByCreatedAtAsc(UUID sessionId, AgentSessionMessageRole role);
}
