package com.ordevia.aidev.verification.infrastructure;

import com.ordevia.aidev.verification.domain.VerificationRunItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface VerificationRunItemJpaRepository extends JpaRepository<VerificationRunItem, UUID> {
    List<VerificationRunItem> findByVerificationRunIdOrderByStartedAtAsc(UUID verificationRunId);
}
