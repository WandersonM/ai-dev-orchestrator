package com.ordevia.aidev.verification.infrastructure;

import com.ordevia.aidev.verification.domain.RepositoryVerificationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryVerificationProfileJpaRepository extends JpaRepository<RepositoryVerificationProfile, UUID> {
    Optional<RepositoryVerificationProfile> findByProjectRepositoryId(UUID projectRepositoryId);
}
