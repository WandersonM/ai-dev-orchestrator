package com.ordevia.aidev.knowledge.infrastructure;

import com.ordevia.aidev.knowledge.domain.ProjectKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectKnowledgeJpaRepository extends JpaRepository<ProjectKnowledge, UUID> {
    List<ProjectKnowledge> findByProjectIdAndActiveTrueOrderByCreatedAtAsc(UUID projectId);
}
