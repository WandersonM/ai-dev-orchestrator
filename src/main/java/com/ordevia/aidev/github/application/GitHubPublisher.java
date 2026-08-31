package com.ordevia.aidev.github.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ordevia.aidev.workspace.application.GitWorktreeManager;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubPublisher {
    private static final Pattern SSH_REMOTE = Pattern.compile("git@github\\.com:([^/]+)/(.+?)(?:\\.git)?$");
    private static final Pattern HTTPS_REMOTE = Pattern.compile("https://github\\.com/([^/]+)/(.+?)(?:\\.git)?$");

    private final WorkItemJpaRepository workItems;
    private final GitWorktreeManager worktrees;
    private final LocalCommandExecutor commands;
    private final Path workspaceRoot;
    private final RestClient github;
    private final boolean enabled;
    private final String token;
    private final String baseBranch;

    public GitHubPublisher(WorkItemJpaRepository workItems,
                           GitWorktreeManager worktrees,
                           LocalCommandExecutor commands,
                           RestClient.Builder restClientBuilder,
                           @Value("${aidev.workspace-root}") String workspaceRoot,
                           @Value("${aidev.github.publish-enabled:false}") boolean enabled,
                           @Value("${aidev.github.token:}") String token,
                           @Value("${aidev.github.api-base-url:https://api.github.com}") String apiBaseUrl,
                           @Value("${aidev.github.base-branch:main}") String baseBranch) {
        this.workItems = workItems;
        this.worktrees = worktrees;
        this.commands = commands;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.enabled = enabled;
        this.token = token;
        this.baseBranch = baseBranch;
        this.github = restClientBuilder.baseUrl(apiBaseUrl).build();
    }

    public WorkItem publish(UUID workItemId) {
        requireEnabled();
        WorkItem item = workItems.findById(workItemId)
                .orElseThrow(() -> new NoSuchElementException("WorkItem not found"));

        if (item.getStatus() != WorkItemStatus.READY_FOR_HUMAN_REVIEW) {
            throw new IllegalStateException("Only READY_FOR_HUMAN_REVIEW work items can be published");
        }
        if (item.getPullRequestNumber() != null) {
            return item;
        }

        GitWorktreeManager.Worktree worktree = worktrees.existing(item.getExternalId());
        ensureCommit(worktree.path(), item);
        push(worktree.path(), worktree.branch());

        RepositorySlug repository = repositorySlug(worktree.path());
        PullRequest created = createDraftPullRequest(repository, item, worktree.branch());
        item.markPublished(created.number(), created.url());
        return workItems.save(item);
    }

    private void ensureCommit(Path worktree, WorkItem item) {
        var status = run(worktree, List.of("git", "status", "--porcelain"), Duration.ofMinutes(1));
        if (!StringUtils.hasText(status.output())) {
            return;
        }

        requireSuccess(run(worktree, List.of("git", "add", "-A"), Duration.ofMinutes(1)), "git add failed");

        String message = "feat(ai): " + safe(item.getExternalId()) + " " + safe(item.getTitle());
        requireSuccess(run(worktree, List.of(
                "git",
                "-c", "user.name=AI Dev Orchestrator",
                "-c", "user.email=ai-dev-orchestrator@localhost",
                "commit", "-m", message), Duration.ofMinutes(2)), "git commit failed");
    }

    private void push(Path worktree, String branch) {
        requireSuccess(
                run(worktree, List.of("git", "push", "-u", "origin", branch), Duration.ofMinutes(3)),
                "git push failed");
    }

    private RepositorySlug repositorySlug(Path worktree) {
        var result = run(worktree, List.of("git", "config", "--get", "remote.origin.url"), Duration.ofMinutes(1));
        requireSuccess(result, "Unable to read git remote origin");
        String remote = result.output().trim();

        Matcher ssh = SSH_REMOTE.matcher(remote);
        if (ssh.matches()) {
            return new RepositorySlug(ssh.group(1), stripGitSuffix(ssh.group(2)));
        }

        Matcher https = HTTPS_REMOTE.matcher(remote);
        if (https.matches()) {
            return new RepositorySlug(https.group(1), stripGitSuffix(https.group(2)));
        }

        throw new IllegalStateException("Only github.com SSH/HTTPS origin remotes are supported for publishing: " + remote);
    }

    private PullRequest createDraftPullRequest(RepositorySlug repo, WorkItem item, String branch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "[" + item.getExternalId() + "] " + item.getTitle());
        body.put("head", branch);
        body.put("base", baseBranch);
        body.put("draft", true);
        body.put("body", pullRequestBody(item));

        JsonNode response = github.post()
                .uri("/repos/{owner}/{repo}/pulls", repo.owner(), repo.name())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("number").asInt(0) == 0) {
            throw new IllegalStateException("GitHub did not return a valid pull request");
        }
        return new PullRequest(response.path("number").asInt(), response.path("html_url").asText());
    }

    private String pullRequestBody(WorkItem item) {
        return """
                ## AI Dev Orchestrator

                This draft pull request was prepared by the local AI development orchestrator and requires human review before merge.

                ## Specification

                %s

                ## Implementation report

                %s

                ## AI review

                %s
                """.formatted(
                nullSafe(item.getSpecification()),
                nullSafe(item.getImplementationReport()),
                nullSafe(item.getReviewReport()));
    }

    private LocalCommandExecutor.CommandResult run(Path worktree, List<String> command, Duration timeout) {
        return commands.execute(workspaceRoot, worktree, command, timeout);
    }

    private void requireSuccess(LocalCommandExecutor.CommandResult result, String message) {
        if (result.exitCode() != 0) {
            throw new IllegalStateException(message + ": " + result.output());
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("GitHub publication is disabled. Set AIDEV_GITHUB_PUBLISH_ENABLED=true to enable it");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("AIDEV_GITHUB_TOKEN is not configured");
        }
    }

    private String stripGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private String safe(String value) {
        if (value == null) return "work-item";
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String nullSafe(String value) {
        return StringUtils.hasText(value) ? value : "_Not provided._";
    }

    private record RepositorySlug(String owner, String name) {}
    private record PullRequest(int number, String url) {}
}
