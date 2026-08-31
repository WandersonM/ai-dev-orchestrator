package com.ordevia.aidev.workitem.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_item")
public class WorkItem {
    @Id private UUID id;
    @Column(name = "project_id") private UUID projectId;
    @Version @Column(nullable = false) private long version;
    @Column(name = "external_id") private String externalId;
    @Column(nullable = false, length = 300) private String title;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 60) private WorkItemStatus status;
    @Column(name = "repository_path", nullable = false, columnDefinition = "text") private String repositoryPath;
    @Column(name = "branch_name") private String branchName;
    @Column(name = "active_workspace_path", columnDefinition = "text") private String activeWorkspacePath;
    @Column(columnDefinition = "text") private String specification;
    @Column(name = "domain_validation_report", columnDefinition = "text") private String domainValidationReport;
    @Column(name = "architecture_plan", columnDefinition = "text") private String architecturePlan;
    @Column(name = "delivery_roles", length = 200) private String deliveryRoles;
    @Column(name = "implementation_report", columnDefinition = "text") private String implementationReport;
    @Column(name = "integration_report", columnDefinition = "text") private String integrationReport;
    @Column(name = "qa_report", columnDefinition = "text") private String qaReport;
    @Column(name = "review_report", columnDefinition = "text") private String reviewReport;
    @Column(name = "security_report", columnDefinition = "text") private String securityReport;
    @Column(name = "release_report", columnDefinition = "text") private String releaseReport;
    @Column(name = "review_iterations", nullable = false) private int reviewIterations;
    @Column(name = "pull_request_number") private Integer pullRequestNumber;
    @Column(name = "pull_request_url", columnDefinition = "text") private String pullRequestUrl;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WorkItem() {}
    public WorkItem(UUID id, String externalId, String title, String description, String repositoryPath){this(id,null,externalId,title,description,repositoryPath);}
    public WorkItem(UUID id, UUID projectId, String externalId, String title, String description, String repositoryPath){this.id=id;this.projectId=projectId;this.externalId=externalId;this.title=title;this.description=description;this.repositoryPath=repositoryPath;this.status=WorkItemStatus.NEW;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
    public void moveTo(WorkItemStatus status){this.status=status;touch();}
    public void setSpecification(String value){specification=value;touch();} public void setDomainValidationReport(String value){domainValidationReport=value;touch();}
    public void setArchitecturePlan(String value){architecturePlan=value;touch();} public void setDeliveryRoles(String value){deliveryRoles=value;touch();}
    public void setImplementationReport(String value){implementationReport=value;touch();} public void setIntegrationReport(String value){integrationReport=value;touch();}
    public void setQaReport(String value){qaReport=value;touch();} public void setReviewReport(String value){reviewReport=value;touch();}
    public void setSecurityReport(String value){securityReport=value;touch();} public void setReleaseReport(String value){releaseReport=value;touch();}
    public void setBranchName(String value){branchName=value;touch();} public void setActiveWorkspacePath(String value){activeWorkspacePath=value;touch();}
    public void incrementReviewIterations(){reviewIterations++;touch();}
    public void markPublished(int number,String url){pullRequestNumber=number;pullRequestUrl=url;publishedAt=Instant.now();touch();}
    private void touch(){updatedAt=Instant.now();}

    public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public long getVersion(){return version;} public String getExternalId(){return externalId;}
    public String getTitle(){return title;} public String getDescription(){return description;} public WorkItemStatus getStatus(){return status;}
    public String getRepositoryPath(){return repositoryPath;} public String getBranchName(){return branchName;} public String getActiveWorkspacePath(){return activeWorkspacePath;}
    public String getSpecification(){return specification;} public String getDomainValidationReport(){return domainValidationReport;} public String getArchitecturePlan(){return architecturePlan;}
    public String getDeliveryRoles(){return deliveryRoles;} public String getImplementationReport(){return implementationReport;} public String getIntegrationReport(){return integrationReport;}
    public String getQaReport(){return qaReport;} public String getReviewReport(){return reviewReport;} public String getSecurityReport(){return securityReport;} public String getReleaseReport(){return releaseReport;}
    public int getReviewIterations(){return reviewIterations;} public Integer getPullRequestNumber(){return pullRequestNumber;} public String getPullRequestUrl(){return pullRequestUrl;}
    public Instant getPublishedAt(){return publishedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
