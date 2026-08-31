package com.ordevia.aidev.workitem.infrastructure;

import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkItemRepositoryBindingJpaRepository extends JpaRepository<WorkItemRepositoryBinding, UUID> {
    List<WorkItemRepositoryBinding> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
    boolean existsByWorkItemIdAndProjectRepositoryId(UUID workItemId, UUID projectRepositoryId);
}
