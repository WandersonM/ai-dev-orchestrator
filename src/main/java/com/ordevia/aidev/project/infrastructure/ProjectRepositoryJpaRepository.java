package com.ordevia.aidev.project.infrastructure;

import com.ordevia.aidev.project.domain.ProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepositoryJpaRepository extends JpaRepository<ProjectRepository, UUID> {
    List<ProjectRepository> findByProjectIdOrderByAliasAsc(UUID projectId);
    Optional<ProjectRepository> findByProjectIdAndAlias(UUID projectId, String alias);
    Optional<ProjectRepository> findFirstByProjectIdAndEnabledTrueOrderByCreatedAtAsc(UUID projectId);
    boolean existsByProjectIdAndAlias(UUID projectId, String alias);
}
