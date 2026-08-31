package com.ordevia.aidev.workflow.application;

import com.ordevia.aidev.agent.domain.*;
import com.ordevia.aidev.execution.domain.AgentExecution;
import com.ordevia.aidev.execution.infrastructure.AgentExecutionJpaRepository;
import com.ordevia.aidev.workitem.domain.*;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.*;

@Service
public class WorkflowEngine {
    private final WorkItemJpaRepository workItems; private final AgentExecutionJpaRepository executions; private final Map<AgentType,Agent> agents; private final Path workspaceRoot;
    public WorkflowEngine(WorkItemJpaRepository workItems, AgentExecutionJpaRepository executions, List<Agent> agentList, @Value("${aidev.workspace-root}") String workspaceRoot) { this.workItems=workItems; this.executions=executions; this.agents=new EnumMap<>(AgentType.class); agentList.forEach(a -> agents.put(a.type(),a)); this.workspaceRoot=Path.of(workspaceRoot).toAbsolutePath().normalize(); }
    @Transactional public WorkItem process(UUID id) { WorkItem item=workItems.findById(id).orElseThrow(() -> new NoSuchElementException("WorkItem not found")); if(item.getStatus()==WorkItemStatus.NEW) return refine(item); throw new IllegalStateException("No executable transition for status " + item.getStatus()); }
    private WorkItem refine(WorkItem item) {
        item.moveTo(WorkItemStatus.REFINING); Agent agent=requiredAgent(AgentType.REFINER); Path repo=workspaceRoot.resolve(item.getRepositoryPath()).normalize(); if(!repo.startsWith(workspaceRoot)) throw new SecurityException("Repository outside workspace root");
        AgentContext context=new AgentContext(item.getId(),repo,item.getBranchName(),item.getTitle(),item.getDescription(),item.getSpecification(),Map.of()); AgentExecution execution=new AgentExecution(UUID.randomUUID(),item.getId(),agent.type(),item.getTitle()); executions.save(execution); AgentResult result=agent.execute(context);
        if(result.success()){ execution.succeed(result.output()); item.setSpecification(result.output()); item.moveTo(WorkItemStatus.READY_FOR_DEVELOPMENT); } else { execution.fail(result.error()); item.moveTo(WorkItemStatus.FAILED); }
        return workItems.save(item);
    }
    private Agent requiredAgent(AgentType type) { Agent agent=agents.get(type); if(agent==null) throw new IllegalStateException("Agent not registered: " + type); return agent; }
}
