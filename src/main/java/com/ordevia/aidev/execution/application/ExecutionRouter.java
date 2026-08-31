package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.EnvironmentProfile;
import com.ordevia.aidev.execution.domain.ExecutionBackendType;
import com.ordevia.aidev.execution.infrastructure.EnvironmentProfileJpaRepository;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import com.ordevia.aidev.workitem.infrastructure.WorkItemJpaRepository;
import com.ordevia.aidev.workitem.infrastructure.WorkItemRepositoryBindingJpaRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class ExecutionRouter {
    private final WorkItemJpaRepository workItems;
    private final WorkItemRepositoryBindingJpaRepository bindings;
    private final ProjectRepositoryJpaRepository repositories;
    private final EnvironmentProfileJpaRepository profiles;
    private final Map<ExecutionBackendType, ExecutionBackend> backends;

    public ExecutionRouter(WorkItemJpaRepository workItems,
                           WorkItemRepositoryBindingJpaRepository bindings,
                           ProjectRepositoryJpaRepository repositories,
                           EnvironmentProfileJpaRepository profiles,
                           List<ExecutionBackend> backendList) {
        this.workItems = workItems;
        this.bindings = bindings;
        this.repositories = repositories;
        this.profiles = profiles;
        this.backends = new EnumMap<>(ExecutionBackendType.class);
        backendList.forEach(backend -> this.backends.put(backend.type(), backend));
    }

    public ExecutionResult execute(UUID workItemId,
                                   Path taskRoot,
                                   Path workingDirectory,
                                   List<String> command) {
        WorkItem item = workItems.findById(workItemId).orElseThrow(() -> new NoSuchElementException("WorkItem not found"));
        Resolution resolution = resolve(item, taskRoot, workingDirectory);
        EnvironmentProfile profile = resolution.profile();
        ExecutionBackendType type = profile == null || !profile.isEnabled()
                ? ExecutionBackendType.LOCAL_WORKTREE
                : profile.getBackendType();
        ExecutionBackend backend = Optional.ofNullable(backends.get(type))
                .orElseThrow(() -> new IllegalStateException("Execution backend not registered: " + type));
        Duration timeout = Duration.ofSeconds(profile == null ? 300 : profile.getTimeoutSeconds());
        Map<String, String> environment = resolveEnvironment(profile);
        return backend.execute(new ExecutionRequest(taskRoot, workingDirectory, command, timeout, profileOrDefault(profile), environment));
    }

    private Resolution resolve(WorkItem item, Path taskRoot, Path workingDirectory) {
        if (item.getProjectId() == null) return new Resolution(null, null);
        List<WorkItemRepositoryBinding> itemBindings = bindings.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        if (itemBindings.isEmpty()) return new Resolution(null, null);

        WorkItemRepositoryBinding selected;
        Path root = taskRoot.toAbsolutePath().normalize();
        Path cwd = workingDirectory.toAbsolutePath().normalize();
        if (!cwd.startsWith(root)) throw new SecurityException("Working directory outside task root");
        Path relative = root.relativize(cwd);
        if (relative.getNameCount() > 0 && !relative.toString().isBlank()) {
            String alias = relative.getName(0).toString();
            selected = itemBindings.stream()
                    .filter(binding -> repositories.findById(binding.getProjectRepositoryId())
                            .map(repo -> repo.getAlias().equals(alias)).orElse(false))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No repository binding matches cwd root alias: " + alias));
        } else if (itemBindings.size() == 1) {
            selected = itemBindings.getFirst();
        } else {
            throw new IllegalArgumentException("run_command requires cwd when WorkItem has multiple repository roots");
        }

        ProjectRepository repository = repositories.findById(selected.getProjectRepositoryId())
                .orElseThrow(() -> new IllegalStateException("Bound repository profile not found"));
        EnvironmentProfile profile = profiles.findByProjectRepositoryId(repository.getId()).orElse(null);
        return new Resolution(repository, profile);
    }

    private Map<String, String> resolveEnvironment(EnvironmentProfile profile) {
        if (profile == null || !profile.isEnabled()) return Map.of();
        Set<String> names = new LinkedHashSet<>();
        addNames(names, profile.getEnvAllowlist());
        addNames(names, profile.getSecretAllowlist());
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null) values.put(name, value);
        }
        return Map.copyOf(values);
    }

    private void addNames(Set<String> names, String csv) {
        if (csv == null || csv.isBlank()) return;
        for (String token : csv.split(",")) {
            String name = token.trim();
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid environment variable name: " + name);
            names.add(name);
        }
    }

    private EnvironmentProfile profileOrDefault(EnvironmentProfile profile) {
        if (profile != null) return profile;
        return new EnvironmentProfile(UUID.randomUUID(), UUID.randomUUID(), ExecutionBackendType.LOCAL_WORKTREE,
                null, com.ordevia.aidev.execution.domain.NetworkPolicy.DENY, 2.0, 2048, 256, 300, null, null, null);
    }

    private record Resolution(ProjectRepository repository, EnvironmentProfile profile) {}
}
