package com.ordevia.aidev.github.infrastructure;

import com.ordevia.aidev.github.domain.WorkItemPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemPullRequestJpaRepository extends JpaRepository<WorkItemPullRequest, UUID> {
    List<WorkItemPullRequest> findByWorkItemIdOrderByRepositoryAliasAsc(UUID workItemId);
    Optional<WorkItemPullRequest> findByWorkItemIdAndRepositoryAlias(UUID workItemId, String repositoryAlias);
}
