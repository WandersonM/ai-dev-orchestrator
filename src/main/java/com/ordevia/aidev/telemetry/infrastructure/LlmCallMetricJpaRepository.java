package com.ordevia.aidev.telemetry.infrastructure;

import com.ordevia.aidev.telemetry.domain.LlmCallMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LlmCallMetricJpaRepository extends JpaRepository<LlmCallMetric, UUID> {
    List<LlmCallMetric> findByWorkItemIdOrderByCreatedAtAsc(UUID workItemId);
    List<LlmCallMetric> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
