package com.ordevia.aidev.api;

import com.ordevia.aidev.workflow.application.WorkflowEngine;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {
    private final WorkItemJpaRepository repository;
    private final WorkflowEngine workflow;
    public WorkItemController(WorkItemJpaRepository repository, WorkflowEngine workflow) { this.repository = repository; this.workflow = workflow; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public WorkItem create(@Valid @RequestBody CreateWorkItemRequest request) { return repository.save(new WorkItem(UUID.randomUUID(), request.externalId(), request.title(), request.description(), request.repositoryPath())); }
    @GetMapping public List<WorkItem> list() { return repository.findAll(); }
    @GetMapping("/{id}") public WorkItem get(@PathVariable UUID id) { return repository.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("WorkItem not found")); }
    @PostMapping("/{id}/start") public WorkItem start(@PathVariable UUID id) { return workflow.process(id); }
    public record CreateWorkItemRequest(String externalId, @NotBlank String title, String description, @NotBlank String repositoryPath) {}
}
