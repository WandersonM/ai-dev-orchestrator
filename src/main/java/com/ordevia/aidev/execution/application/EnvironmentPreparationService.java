package com.ordevia.aidev.execution.application;

import com.ordevia.aidev.execution.domain.EnvironmentProfile;
import com.ordevia.aidev.execution.infrastructure.EnvironmentProfileJpaRepository;
import com.ordevia.aidev.project.domain.ProjectRepository;
import com.ordevia.aidev.project.infrastructure.ProjectRepositoryJpaRepository;
import com.ordevia.aidev.workitem.domain.WorkItem;
import com.ordevia.aidev.workitem.domain.WorkItemRepositoryBinding;
import com.ordevia.aidev.workitem.infrastructure.WorkItemRepositoryBindingJpaRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class EnvironmentPreparationService {
    private final WorkItemRepositoryBindingJpaRepository bindings;
    private final ProjectRepositoryJpaRepository repositories;
    private final EnvironmentProfileJpaRepository profiles;
    private final SafeCommandLineParser parser;
    private final ExecutionRouter executionRouter;

    public EnvironmentPreparationService(WorkItemRepositoryBindingJpaRepository bindings,
                                         ProjectRepositoryJpaRepository repositories,
                                         EnvironmentProfileJpaRepository profiles,
                                         SafeCommandLineParser parser,
                                         ExecutionRouter executionRouter) {
        this.bindings = bindings;
        this.repositories = repositories;
        this.profiles = profiles;
        this.parser = parser;
        this.executionRouter = executionRouter;
    }

    public void prepare(WorkItem item, Path taskRoot) {
        List<WorkItemRepositoryBinding> itemBindings = bindings.findByWorkItemIdOrderByCreatedAtAsc(item.getId());
        for (WorkItemRepositoryBinding binding : itemBindings) {
            ProjectRepository repository = repositories.findById(binding.getProjectRepositoryId())
                    .orElseThrow(() -> new IllegalStateException("Bound repository profile not found"));
            EnvironmentProfile profile = profiles.findByProjectRepositoryId(repository.getId()).orElse(null);
            if (profile == null || !profile.isEnabled() || profile.getSetupCommand() == null || profile.getSetupCommand().isBlank()) continue;

            Path marker = taskRoot.resolve(".aidev-runtime").resolve(repository.getAlias() + ".prepared").normalize();
            if (!marker.startsWith(taskRoot.toAbsolutePath().normalize())) throw new SecurityException("Invalid environment marker path");
            if (Files.exists(marker)) continue;

            Path cwd = taskRoot.resolve(repository.getAlias()).normalize();
            var result = executionRouter.execute(item.getId(), taskRoot, cwd, parser.parse(profile.getSetupCommand()));
            if (!result.success()) {
                throw new IllegalStateException("Environment setup failed for repository " + repository.getAlias() + ": " + result.output());
            }
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker,
                        "preparedAt=" + Instant.now() + "\nbackend=" + result.backend() + "\n",
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to persist environment preparation marker", e);
            }
        }
    }
}
