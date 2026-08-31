package com.ordevia.aidev.workitem.infrastructure;

import com.ordevia.aidev.workitem.domain.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkItemJpaRepository extends JpaRepository<WorkItem, UUID> {}
