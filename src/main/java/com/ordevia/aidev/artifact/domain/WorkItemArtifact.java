package com.ordevia.aidev.artifact.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="work_item_artifact")
public class WorkItemArtifact {
    @Id private UUID id;
    @Column(name="work_item_id",nullable=false) private UUID workItemId;
    @Column(name="session_id") private UUID sessionId;
    @Column(name="repository_alias",length=120) private String repositoryAlias;
    @Enumerated(EnumType.STRING) @Column(name="artifact_type",nullable=false,length=40) private ArtifactType artifactType;
    @Column(name="relative_path",nullable=false,columnDefinition="text") private String relativePath;
    @Column(name="content_type",length=200) private String contentType;
    @Column(columnDefinition="text") private String description;
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected WorkItemArtifact(){}
    public WorkItemArtifact(UUID id,UUID workItemId,UUID sessionId,String repositoryAlias,ArtifactType artifactType,String relativePath,
                            String contentType,String description,String sha256,long sizeBytes){
        this.id=id;this.workItemId=workItemId;this.sessionId=sessionId;this.repositoryAlias=repositoryAlias;this.artifactType=artifactType;
        this.relativePath=relativePath;this.contentType=contentType;this.description=description;this.sha256=sha256;this.sizeBytes=sizeBytes;this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getWorkItemId(){return workItemId;} public UUID getSessionId(){return sessionId;}
    public String getRepositoryAlias(){return repositoryAlias;} public ArtifactType getArtifactType(){return artifactType;} public String getRelativePath(){return relativePath;}
    public String getContentType(){return contentType;} public String getDescription(){return description;} public String getSha256(){return sha256;} public long getSizeBytes(){return sizeBytes;}
    public Instant getCreatedAt(){return createdAt;}
}
