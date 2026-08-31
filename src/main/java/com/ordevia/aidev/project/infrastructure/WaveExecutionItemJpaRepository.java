package com.ordevia.aidev.project.infrastructure;

import com.ordevia.aidev.project.domain.WaveExecutionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaveExecutionItemJpaRepository extends JpaRepository<WaveExecutionItem, UUID> {
    List<WaveExecutionItem> findByWaveExecutionIdOrderByStartedAtAsc(UUID waveExecutionId);
}
