package com.ordevia.aidev.artifact.infrastructure;

import com.ordevia.aidev.artifact.domain.WorkItemArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkItemArtifactJpaRepository extends JpaRepository<WorkItemArtifact, UUID> {
    List<WorkItemArtifact> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
    List<WorkItemArtifact> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
