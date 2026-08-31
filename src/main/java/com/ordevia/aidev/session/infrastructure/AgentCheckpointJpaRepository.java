package com.ordevia.aidev.session.infrastructure;

import com.ordevia.aidev.session.domain.AgentCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentCheckpointJpaRepository extends JpaRepository<AgentCheckpoint, UUID> {
    List<AgentCheckpoint> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);
}
