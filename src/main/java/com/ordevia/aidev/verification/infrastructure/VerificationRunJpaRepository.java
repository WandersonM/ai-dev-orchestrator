package com.ordevia.aidev.verification.infrastructure;

import com.ordevia.aidev.verification.domain.VerificationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface VerificationRunJpaRepository extends JpaRepository<VerificationRun, UUID> {
    List<VerificationRun> findByWorkItemIdOrderByStartedAtDesc(UUID workItemId);
}
