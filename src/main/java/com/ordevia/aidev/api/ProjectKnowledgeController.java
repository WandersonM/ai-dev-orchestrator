package com.ordevia.aidev.api;

import com.ordevia.aidev.knowledge.application.ProjectKnowledgeService;
import com.ordevia.aidev.knowledge.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/knowledge")
public class ProjectKnowledgeController {
    private final ProjectKnowledgeService knowledge;

    public ProjectKnowledgeController(ProjectKnowledgeService knowledge){this.knowledge=knowledge;}

    @GetMapping
    public List<ProjectKnowledge> active(@PathVariable UUID projectId){return knowledge.active(projectId);}

    @PostMapping
    public ProjectKnowledge add(@PathVariable UUID projectId,@Valid @RequestBody CreateKnowledgeRequest request){
        return knowledge.add(projectId,request.type(),request.statement(),request.sourceType(),request.sourceRef(),request.confidence(),request.createdBy());
    }

    @PostMapping("/{id}/supersede")
    public ProjectKnowledge supersede(@PathVariable UUID projectId,@PathVariable UUID id,@RequestBody(required=false) SupersedeRequest request){
        return knowledge.supersede(projectId,id,request==null?null:request.actor());
    }

    public record CreateKnowledgeRequest(@NotNull KnowledgeType type,@NotBlank String statement,@NotBlank String sourceType,
                                         String sourceRef,KnowledgeConfidence confidence,String createdBy){}
    public record SupersedeRequest(String actor){}
}
