package com.ordevia.aidev.execution.infrastructure;

import com.ordevia.aidev.execution.domain.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgentExecutionJpaRepository extends JpaRepository<AgentExecution, UUID> {}
