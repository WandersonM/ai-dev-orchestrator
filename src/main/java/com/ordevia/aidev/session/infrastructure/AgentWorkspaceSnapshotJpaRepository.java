package com.ordevia.aidev.session.infrastructure;

import com.ordevia.aidev.session.domain.AgentWorkspaceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AgentWorkspaceSnapshotJpaRepository extends JpaRepository<AgentWorkspaceSnapshot, UUID> {
    List<AgentWorkspaceSnapshot> findByCheckpointIdOrderByRepositoryAliasAsc(UUID checkpointId);
    List<AgentWorkspaceSnapshot> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    boolean existsByCheckpointId(UUID checkpointId);
}
