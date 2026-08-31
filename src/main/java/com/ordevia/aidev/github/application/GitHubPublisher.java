package com.ordevia.aidev.github.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.github.domain.WorkItemPullRequest;
import com.ordevia.aidev.github.infrastructure.WorkItemPullRequestJpaRepository;
import com.ordevia.aidev.workspace.application.GitWorktreeManager;
import com.ordevia.aidev.workspace.application.MultiRepositoryWorkspaceManager;
import com.ordevia.aidev.workspace.infrastructure.LocalCommandExecutor;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemStatus;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubPublisher {
    private static final Pattern SSH_REMOTE = Pattern.compile("git@github\\.com:([^/]+)/(.+?)(?:\\.git)?$");
    private static final Pattern HTTPS_REMOTE = Pattern.compile("https://github\\.com/([^/]+)/(.+?)(?:\\.git)?$");

    private final WorkItemJpaRepository workItems;
    private final WorkItemPullRequestJpaRepository pullRequests;
    private final MultiRepositoryWorkspaceManager multiWorkspace;
    private final LocalCommandExecutor commands;
    private final Path workspaceRoot;
    private final RestClient github;
    private final boolean enabled;
    private final String token;

    public GitHubPublisher(WorkItemJpaRepository workItems,
                           WorkItemPullRequestJpaRepository pullRequests,
                           MultiRepositoryWorkspaceManager multiWorkspace,
                           LocalCommandExecutor commands,
                           RestClient.Builder restClientBuilder,
                           @Value("${aidev.workspace-root}") String workspaceRoot,
                           @Value("${aidev.github.publish-enabled:false}") boolean enabled,
                           @Value("${aidev.github.token:}") String token,
                           @Value("${aidev.github.api-base-url:https://api.github.com}") String apiBaseUrl) {
        this.workItems = workItems;
        this.pullRequests = pullRequests;
        this.multiWorkspace = multiWorkspace;
        this.commands = commands;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.enabled = enabled;
        this.token = token;
        this.github = restClientBuilder.baseUrl(apiBaseUrl).build();
    }

    public WorkItem publish(UUID workItemId) {
        requireEnabled();
        WorkItem item = workItems.findById(workItemId)
                .orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
        if (item.getStatus() != WorkItemStatus.READY_FOR_HUMAN_REVIEW) {
            throw new IllegalStateException("Only READY_FOR_HUMAN_REVIEW work items can be published");
        }

        var workspace = multiWorkspace.prepare(item);
        WorkItemPullRequest firstCreated = null;
        for (GitWorktreeManager.Worktree worktree : workspace.worktrees()) {
            Optional<WorkItemPullRequest> existing = pullRequests.findByWorkItemIdAndRepositoryAlias(item.getId(), worktree.repositoryAlias());
            if (existing.isPresent()) {
                if (firstCreated == null) firstCreated = existing.get();
                continue;
            }

            boolean changed = ensureCommit(worktree.path(), item);
            if (!changed && !hasCommitsAhead(worktree.path(), worktree.baseBranch())) continue;

            push(worktree.path(), worktree.branch());
            RepositorySlug repository = repositorySlug(worktree.path());
            PullRequest created = createDraftPullRequest(repository, item, worktree);
            WorkItemPullRequest persisted = pullRequests.save(new WorkItemPullRequest(
                    UUID.randomUUID(), item.getId(), worktree.repositoryAlias(), repository.owner() + "/" + repository.name(),
                    created.number(), created.url(), worktree.branch(), worktree.baseBranch()));
            if (firstCreated == null) firstCreated = persisted;
        }

        if (firstCreated == null) throw new IllegalStateException("No repository has changes to publish");
        if (item.getPullRequestNumber() == null) item.markPublished(firstCreated.getPullRequestNumber(), firstCreated.getPullRequestUrl());
        return workItems.save(item);
    }

    public List<WorkItemPullRequest> list(UUID workItemId) {
        if (!workItems.existsById(workItemId)) throw new NoSuchElementException("WorkItem not found");
        return pullRequests.findByWorkItemIdOrderByRepositoryAliasAsc(workItemId);
    }

    private boolean ensureCommit(Path worktree, WorkItem item) {
        var status = run(worktree, List.of("git", "status", "--porcelain"), Duration.ofMinutes(1));
        requireSuccess(status, "git status failed");
        if (!StringUtils.hasText(status.output())) return false;

        requireSuccess(run(worktree, List.of("git", "add", "-A"), Duration.ofMinutes(1)), "git add failed");
        String message = "feat(ai): " + safe(item.getExternalId()) + " " + safe(item.getTitle());
        requireSuccess(run(worktree, List.of(
                "git", "-c", "user.name=AI Dev Orchestrator", "-c", "user.email=ai-dev-orchestrator@localhost",
                "commit", "-m", message), Duration.ofMinutes(2)), "git commit failed");
        return true;
    }

    private boolean hasCommitsAhead(Path worktree, String baseBranch) {
        var remote = run(worktree, List.of("git", "rev-list", "--count", "origin/" + baseBranch + "..HEAD"), Duration.ofMinutes(1));
        if (remote.exitCode() == 0) return parsePositive(remote.output());
        var local = run(worktree, List.of("git", "rev-list", "--count", baseBranch + "..HEAD"), Duration.ofMinutes(1));
        return local.exitCode() == 0 && parsePositive(local.output());
    }

    private boolean parsePositive(String value) {
        try { return Integer.parseInt(value.trim()) > 0; }
        catch (Exception ignored) { return false; }
    }

    private void push(Path worktree, String branch) {
        requireSuccess(run(worktree, List.of("git", "push", "-u", "origin", branch), Duration.ofMinutes(3)), "git push failed");
    }

    private RepositorySlug repositorySlug(Path worktree) {
        var result = run(worktree, List.of("git", "config", "--get", "remote.origin.url"), Duration.ofMinutes(1));
        requireSuccess(result, "Unable to read git remote origin");
        String remote = result.output().trim();
        Matcher ssh = SSH_REMOTE.matcher(remote);
        if (ssh.matches()) return new RepositorySlug(ssh.group(1), stripGitSuffix(ssh.group(2)));
        Matcher https = HTTPS_REMOTE.matcher(remote);
        if (https.matches()) return new RepositorySlug(https.group(1), stripGitSuffix(https.group(2)));
        throw new IllegalStateException("Only github.com SSH/HTTPS origin remotes are supported for publishing: " + remote);
    }

    private PullRequest createDraftPullRequest(RepositorySlug repo, WorkItem item, GitWorktreeManager.Worktree worktree) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "[" + item.getExternalId() + "] " + item.getTitle() + " (" + worktree.repositoryAlias() + ")");
        body.put("head", worktree.branch());
        body.put("base", worktree.baseBranch());
        body.put("draft", true);
        body.put("body", pullRequestBody(item, worktree.repositoryAlias()));

        JsonNode response = github.post()
                .uri("/repos/{owner}/{repo}/pulls", repo.owner(), repo.name())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null || response.path("number").asInt(0) == 0) throw new IllegalStateException("GitHub did not return a valid pull request");
        return new PullRequest(response.path("number").asInt(), response.path("html_url").asText());
    }

    private String pullRequestBody(WorkItem item, String repositoryAlias) {
        return """
                ## AI Dev Orchestrator

                Coordinated repository: `%s`
                Work item: `%s`

                This draft pull request is one part of a potentially multi-repository delivery and requires human review before merge.

                ## Specification
                %s

                ## Architecture
                %s

                ## Implementation report
                %s

                ## QA
                %s

                ## Security review
                %s

                ## AI review
                %s
                """.formatted(repositoryAlias, safe(item.getExternalId()), nullSafe(item.getSpecification()),
                nullSafe(item.getArchitecturePlan()), nullSafe(item.getImplementationReport()), nullSafe(item.getQaReport()),
                nullSafe(item.getSecurityReport()), nullSafe(item.getReviewReport()));
    }

    private LocalCommandExecutor.CommandResult run(Path worktree, List<String> command, Duration timeout) {
        return commands.execute(workspaceRoot, worktree, command, timeout);
    }

    private void requireSuccess(LocalCommandExecutor.CommandResult result, String message) {
        if (result.exitCode() != 0) throw new IllegalStateException(message + ": " + result.output());
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("GitHub publication is disabled. Set AIDEV_GITHUB_PUBLISH_ENABLED=true to enable it");
        if (!StringUtils.hasText(token)) throw new IllegalStateException("AIDEV_GITHUB_TOKEN is not configured");
    }

    private String stripGitSuffix(String value) { return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value; }
    private String safe(String value) { return value == null ? "work-item" : value.replaceAll("[\\r\\n]+", " ").trim(); }
    private String nullSafe(String value) { return StringUtils.hasText(value) ? value : "_Not provided._"; }

    private record RepositorySlug(String owner, String name) {}
    private record PullRequest(int number, String url) {}
}
