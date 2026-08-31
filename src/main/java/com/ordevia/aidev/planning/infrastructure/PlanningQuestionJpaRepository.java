package com.ordevia.aidev.planning.infrastructure;

import com.ordevia.aidev.planning.domain.PlanningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanningQuestionJpaRepository extends JpaRepository<PlanningQuestion, UUID> {
    List<PlanningQuestion> findBySessionIdOrderByRoundAscCreatedAtAsc(UUID sessionId);
    List<PlanningQuestion> findBySessionIdAndRoundOrderByCreatedAtAsc(UUID sessionId, int round);
}
