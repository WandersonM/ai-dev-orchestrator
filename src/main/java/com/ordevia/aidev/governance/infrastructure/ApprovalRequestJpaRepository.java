package com.ordevia.aidev.governance.infrastructure;

import com.ordevia.aidev.governance.domain.ApprovalRequest;
import com.ordevia.aidev.governance.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestJpaRepository extends JpaRepository<ApprovalRequest, UUID> {
    Optional<ApprovalRequest> findBySessionIdAndStepNumberAndToolNameAndArgumentsHash(UUID sessionId,int stepNumber,String toolName,String argumentsHash);
    List<ApprovalRequest> findByStatusOrderByRequestedAtAsc(ApprovalStatus status);
    List<ApprovalRequest> findByWorkItemIdOrderByRequestedAtDesc(UUID workItemId);
}
