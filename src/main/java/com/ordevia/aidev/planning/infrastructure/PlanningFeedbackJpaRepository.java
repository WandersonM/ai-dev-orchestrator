package com.ordevia.aidev.planning.infrastructure;

import com.ordevia.aidev.planning.domain.PlanningFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanningFeedbackJpaRepository extends JpaRepository<PlanningFeedback, UUID> {
    List<PlanningFeedback> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
