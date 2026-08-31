package com.ordevia.aidev.knowledge.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="project_knowledge")
public class ProjectKnowledge {
    @Id private UUID id;
    @Column(name="project_id",nullable=false) private UUID projectId;
    @Enumerated(EnumType.STRING) @Column(name="knowledge_type",nullable=false,length=40) private KnowledgeType knowledgeType;
    @Column(nullable=false,columnDefinition="text") private String statement;
    @Column(name="source_type",nullable=false,length=60) private String sourceType;
    @Column(name="source_ref",columnDefinition="text") private String sourceRef;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private KnowledgeConfidence confidence;
    @Column(nullable=false) private boolean active;
    @Column(name="created_by",length=200) private String createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="superseded_at") private Instant supersededAt;

    protected ProjectKnowledge() {}

    public ProjectKnowledge(UUID id, UUID projectId, KnowledgeType type, String statement,
                            String sourceType, String sourceRef, KnowledgeConfidence confidence, String createdBy) {
        this.id=id; this.projectId=projectId; this.knowledgeType=type; this.statement=statement;
        this.sourceType=sourceType; this.sourceRef=sourceRef; this.confidence=confidence;
        this.createdBy=createdBy; this.active=true; this.createdAt=Instant.now();
    }

    public void supersede(){if(active){active=false;supersededAt=Instant.now();}}

    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public KnowledgeType getKnowledgeType(){return knowledgeType;}
    public String getStatement(){return statement;} public String getSourceType(){return sourceType;} public String getSourceRef(){return sourceRef;}
    public KnowledgeConfidence getConfidence(){return confidence;} public boolean isActive(){return active;} public String getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;} public Instant getSupersededAt(){return supersededAt;}
}
