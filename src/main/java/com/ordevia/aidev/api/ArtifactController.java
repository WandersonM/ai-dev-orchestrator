package com.ordevia.aidev.api;

import com.ordevia.aidev.artifact.application.ArtifactService;
import com.ordevia.aidev.artifact.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {
    private final ArtifactService artifacts;
    public ArtifactController(ArtifactService artifacts){this.artifacts=artifacts;}

    @PostMapping
    public WorkItemArtifact register(@Valid @RequestBody RegisterArtifactRequest request){
        return artifacts.register(request.workItemId(),request.sessionId(),request.repositoryAlias(),request.type(),request.workspaceRelativePath(),request.contentType(),request.description());
    }

    @GetMapping("/work-item/{workItemId}")
    public List<WorkItemArtifact> byWorkItem(@PathVariable UUID workItemId){return artifacts.byWorkItem(workItemId);}

    @GetMapping("/session/{sessionId}")
    public List<WorkItemArtifact> bySession(@PathVariable UUID sessionId){return artifacts.bySession(sessionId);}

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id){
        WorkItemArtifact artifact=artifacts.get(id);Resource resource=artifacts.resource(id);
        MediaType media=MediaType.APPLICATION_OCTET_STREAM;
        try{if(artifact.getContentType()!=null)media=MediaType.parseMediaType(artifact.getContentType());}catch(Exception ignored){}
        return ResponseEntity.ok().contentType(media).contentLength(artifact.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+resource.getFilename()+"\"").body(resource);
    }

    public record RegisterArtifactRequest(@NotNull UUID workItemId,UUID sessionId,String repositoryAlias,@NotNull ArtifactType type,
                                          @NotBlank String workspaceRelativePath,String contentType,String description){}
}
