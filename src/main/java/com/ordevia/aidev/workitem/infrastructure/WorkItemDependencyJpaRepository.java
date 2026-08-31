package com.ordevia.aidev.workitem.infrastructure;

import com.ordevia.aidev.workitem.domain.WorkItemDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkItemDependencyJpaRepository extends JpaRepository<WorkItemDependency, UUID> {
    List<WorkItemDependency> findByWorkItemId(UUID workItemId);
    List<WorkItemDependency> findByWorkItemIdIn(List<UUID> workItemIds);
    boolean existsByWorkItemIdAndBlockedByWorkItemId(UUID workItemId, UUID blockedByWorkItemId);
}
