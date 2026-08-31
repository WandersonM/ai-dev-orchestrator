package com.ordevia.aidev.project.infrastructure;

import com.ordevia.aidev.project.domain.WaveExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaveExecutionJpaRepository extends JpaRepository<WaveExecution, UUID> {
    List<WaveExecution> findByProjectIdOrderByStartedAtDesc(UUID projectId);
}
