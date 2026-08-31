package com.ordevia.aidev.execution.infrastructure;

import com.ordevia.aidev.execution.domain.EnvironmentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnvironmentProfileJpaRepository extends JpaRepository<EnvironmentProfile, UUID> {
    Optional<EnvironmentProfile> findByProjectRepositoryId(UUID projectRepositoryId);
}
