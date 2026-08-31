package com.ordevia.aidev.planning.infrastructure;

import com.ordevia.aidev.planning.domain.PlanningSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanningSessionJpaRepository extends JpaRepository<PlanningSession, UUID> {
    Optional<PlanningSession> findByWorkItemId(UUID workItemId);
}
