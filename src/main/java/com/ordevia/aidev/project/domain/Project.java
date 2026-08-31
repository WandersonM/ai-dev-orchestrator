package com.ordevia.aidev.project.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project")
public class Project {
    @Id private UUID id;
    @Column(nullable = false, length = 200) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "repository_path", nullable = false, columnDefinition = "text") private String repositoryPath;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Project() {}

    public Project(UUID id, String name, String description, String repositoryPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.repositoryPath = repositoryPath;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRepositoryPath() { return repositoryPath; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
