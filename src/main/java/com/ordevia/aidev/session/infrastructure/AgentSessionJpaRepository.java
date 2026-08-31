package com.ordevia.aidev.session.infrastructure;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.session.domain.AgentSession;
import com.ordevia.aidev.session.domain.AgentSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentSessionJpaRepository extends JpaRepository<AgentSession, UUID> {
    List<AgentSession> findByWorkItemIdOrderByCreatedAtDesc(UUID workItemId);
    Optional<AgentSession> findFirstByWorkItemIdAndAgentTypeAndStatusInOrderByCreatedAtDesc(UUID workItemId, AgentType agentType, List<AgentSessionStatus> statuses);
}
