package com.ordevia.aidev.artifact.application;

import com.ordevia.aidev.artifact.domain.*;
import com.ordevia.aidev.artifact.infrastructure.WorkItemArtifactJpaRepository;
import com.ordevia.aidev.audit.application.AuditService;
import com.ordevia.aidev.session.infrastructure.AgentSessionJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ArtifactService {
    private final WorkItemArtifactJpaRepository artifacts;
    private final WorkItemJpaRepository workItems;
    private final AgentSessionJpaRepository sessions;
    private final AuditService audit;
    private final Path workspaceRoot;

    public ArtifactService(WorkItemArtifactJpaRepository artifacts, WorkItemJpaRepository workItems,
                           AgentSessionJpaRepository sessions, AuditService audit,
                           @Value("${aidev.workspace-root}") String workspaceRoot) {
        this.artifacts=artifacts;this.workItems=workItems;this.sessions=sessions;this.audit=audit;
        this.workspaceRoot=Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public WorkItemArtifact register(UUID workItemId,UUID sessionId,String repositoryAlias,ArtifactType type,
                                     String workspaceRelativePath,String contentType,String description){
        if(!workItems.existsById(workItemId))throw new NoSuchElementException("WorkItem not found");
        if(sessionId!=null){var session=sessions.findById(sessionId).orElseThrow(()->new NoSuchElementException("Agent session not found"));if(!workItemId.equals(session.getWorkItemId()))throw new IllegalArgumentException("Session does not belong to WorkItem");}
        Path file=resolve(workspaceRelativePath);if(!Files.isRegularFile(file))throw new IllegalArgumentException("Artifact file does not exist: "+workspaceRelativePath);
        try{
            String relative=workspaceRoot.relativize(file).toString().replace('\\','/');
            String detected=contentType==null||contentType.isBlank()?Files.probeContentType(file):contentType;
            WorkItemArtifact artifact=artifacts.save(new WorkItemArtifact(UUID.randomUUID(),workItemId,sessionId,repositoryAlias,type,relative,detected,description,sha256(file),Files.size(file)));
            audit.append(workItemId,sessionId,"ARTIFACT_REGISTERED","SYSTEM",null,"WorkItemArtifact",artifact.getId().toString(),Map.of(
                    "type",type.name(),"path",relative,"sizeBytes",artifact.getSizeBytes(),"sha256",artifact.getSha256()));
            return artifact;
        }catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new IllegalStateException("Unable to register artifact",e);}
    }

    @Transactional(readOnly=true) public List<WorkItemArtifact> byWorkItem(UUID id){return artifacts.findByWorkItemIdOrderByCreatedAtAsc(id);}
    @Transactional(readOnly=true) public List<WorkItemArtifact> bySession(UUID id){return artifacts.findBySessionIdOrderByCreatedAtAsc(id);}
    @Transactional(readOnly=true) public WorkItemArtifact get(UUID id){return artifacts.findById(id).orElseThrow(()->new NoSuchElementException("Artifact not found"));}

    public Resource resource(UUID id){WorkItemArtifact artifact=get(id);return new FileSystemResource(resolve(artifact.getRelativePath()));}

    private Path resolve(String relative){
        if(relative==null||relative.isBlank())throw new IllegalArgumentException("Artifact path is required");
        Path file=workspaceRoot.resolve(relative).normalize();if(!file.startsWith(workspaceRoot))throw new SecurityException("Artifact path escapes workspace root");return file;
    }

    private String sha256(Path file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[64*1024];int read;while((read=in.read(buffer))>=0)digest.update(buffer,0,read);}
        return HexFormat.of().formatHex(digest.digest());
    }
}
