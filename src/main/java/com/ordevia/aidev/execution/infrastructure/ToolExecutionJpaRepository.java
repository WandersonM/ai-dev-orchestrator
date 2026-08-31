package com.ordevia.aidev.execution.infrastructure;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.execution.domain.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ToolExecutionJpaRepository extends JpaRepository<ToolExecution, UUID> {
    List<ToolExecution> findByWorkItemIdAndAgentTypeOrderByStepNumberAsc(UUID workItemId, AgentType agentType);
}
