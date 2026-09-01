package com.ordevia.aidev.config;

import com.ordevia.aidev.agent.domain.AgentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

@Service
public class LocalSettingsService {
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final Environment environment;
    private final Path configPath;
    private final Yaml yaml;

    public LocalSettingsService(Environment environment,
                                @Value("${aidev.local-settings.path:./config/application-local.yml}") String configPath) {
        this.environment = environment;
        this.configPath = Path.of(configPath).toAbsolutePath().normalize();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setSplitLines(false);
        this.yaml = new Yaml(options);
    }

    public LocalSettingsView get() {
        Map<String, Object> values = readFile();
        return view(values, false);
    }

    public LocalSettingsView save(UpdateLocalSettingsRequest request) {
        requireLocalProfile();
        Map<String, Object> root = readFile();

        put(root, "spring.datasource.url", request.databaseUrl());
        put(root, "spring.datasource.username", request.databaseUsername());
        putSecret(root, "spring.datasource.password", request.databasePassword());
        put(root, "spring.flyway.enabled", true);
        put(root, "spring.flyway.clean-disabled", true);
        put(root, "spring.flyway.validate-on-migrate", true);

        putSecret(root, "aidev.llm.openai.api-key", request.openAiApiKey());
        put(root, "aidev.llm.openai.base-url", request.openAiBaseUrl());
        putSecret(root, "aidev.llm.gemini.api-key", request.geminiApiKey());
        put(root, "aidev.llm.gemini.base-url", request.geminiBaseUrl());

        put(root, "aidev.github.publish-enabled", request.githubPublishEnabled());
        putSecret(root, "aidev.github.token", request.githubToken());
        put(root, "aidev.github.base-branch", request.githubBaseBranch());

        put(root, "aidev.trello.enabled", request.trelloEnabled());
        putSecret(root, "aidev.trello.api-key", request.trelloApiKey());
        putSecret(root, "aidev.trello.token", request.trelloToken());

        put(root, "aidev.mcp.enabled", request.mcpEnabled());

        put(root, "aidev.codex.enabled", request.codexEnabled());
        put(root, "aidev.codex.binary", request.codexBinary());
        put(root, "aidev.codex.model", request.codexModel());
        if (request.codexRoles() != null) {
            put(root, "aidev.codex.roles", request.codexRoles().stream().map(AgentType::name).toList());
        }

        if (request.routes() != null) {
            request.routes().forEach((route, config) -> {
                if (config == null) return;
                put(root, "aidev.llm.routes." + normalizeRoute(route) + ".provider", config.provider());
                put(root, "aidev.llm.routes." + normalizeRoute(route) + ".model", config.model());
            });
        }

        writeFile(root);
        return view(root, true);
    }

    public Path configPath() {
        return configPath;
    }

    private LocalSettingsView view(Map<String, Object> file) {
        return view(file, false);
    }

    private LocalSettingsView view(Map<String, Object> file, boolean restartRequired) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        return new LocalSettingsView(
                local,
                configPath.toString(),
                Files.exists(configPath),
                restartRequired,
                value(file, "spring.datasource.url", "spring.datasource.url", "jdbc:postgresql://localhost:5432/aidev"),
                value(file, "spring.datasource.username", "spring.datasource.username", "aidev"),
                secretConfigured(file, "spring.datasource.password", "spring.datasource.password"),
                value(file, "aidev.llm.openai.base-url", "aidev.llm.openai.base-url", "https://api.openai.com"),
                secretConfigured(file, "aidev.llm.openai.api-key", "aidev.llm.openai.api-key"),
                value(file, "aidev.llm.gemini.base-url", "aidev.llm.gemini.base-url", "https://generativelanguage.googleapis.com"),
                secretConfigured(file, "aidev.llm.gemini.api-key", "aidev.llm.gemini.api-key"),
                boolValue(file, "aidev.github.publish-enabled", "aidev.github.publish-enabled", false),
                secretConfigured(file, "aidev.github.token", "aidev.github.token"),
                value(file, "aidev.github.base-branch", "aidev.github.base-branch", "main"),
                boolValue(file, "aidev.trello.enabled", "aidev.trello.enabled", false),
                secretConfigured(file, "aidev.trello.api-key", "aidev.trello.api-key"),
                secretConfigured(file, "aidev.trello.token", "aidev.trello.token"),
                boolValue(file, "aidev.mcp.enabled", "aidev.mcp.enabled", false),
                boolValue(file, "aidev.codex.enabled", "aidev.codex.enabled", false),
                value(file, "aidev.codex.binary", "aidev.codex.binary", "codex"),
                value(file, "aidev.codex.model", "aidev.codex.model", ""),
                enumList(file, "aidev.codex.roles", "aidev.codex.roles"),
                routeViews(file)
        );
    }

    private Map<String, RouteView> routeViews(Map<String, Object> file) {
        Map<String, RouteView> result = new LinkedHashMap<>();
        for (String route : List.of("refinement", "architecture", "backend", "frontend", "qa", "review", "critic", "security", "integration", "release", "domain-validation")) {
            String key = "aidev.llm.routes." + route;
            result.put(route, new RouteView(
                    value(file, key + ".provider", key + ".provider", ""),
                    value(file, key + ".model", key + ".model", "")
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readFile() {
        if (!Files.exists(configPath)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object loaded = yaml.load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return deepCopy((Map<String, Object>) map);
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read local settings file: " + configPath, e);
        }
    }

    private Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) nested;
                result.put(key, deepCopy(typed));
            } else if (value instanceof Collection<?> collection) {
                result.put(key, new ArrayList<>(collection));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private void writeFile(Map<String, Object> root) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                yaml.dump(root, writer);
            }
            try {
                Files.setPosixFilePermissions(configPath, OWNER_ONLY);
            } catch (UnsupportedOperationException ignored) {
                // Windows and other non-POSIX filesystems do not expose POSIX permissions.
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not write local settings file: " + configPath, e);
        }
    }

    private void requireLocalProfile() {
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            throw new IllegalStateException("Local settings can only be changed while the 'local' Spring profile is active");
        }
    }

    @SuppressWarnings("unchecked")
    private void put(Map<String, Object> root, String path, Object value) {
        if (value == null) return;
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = current.get(parts[i]);
            if (!(existing instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                current = (Map<String, Object>) existing;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private void putSecret(Map<String, Object> root, String path, String value) {
        if (StringUtils.hasText(value)) put(root, path, value.trim());
    }

    private String normalizeRoute(String route) {
        return route == null ? "" : route.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private Object nested(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
            if (current == null) return null;
        }
        return current;
    }

    private String value(Map<String, Object> file, String filePath, String property, String fallback) {
        Object fromFile = nested(file, filePath);
        if (fromFile != null) return String.valueOf(fromFile);
        return environment.getProperty(property, fallback);
    }

    private boolean boolValue(Map<String, Object> file, String filePath, String property, boolean fallback) {
        Object fromFile = nested(file, filePath);
        if (fromFile != null) return Boolean.parseBoolean(String.valueOf(fromFile));
        return environment.getProperty(property, Boolean.class, fallback);
    }

    private boolean secretConfigured(Map<String, Object> file, String filePath, String property) {
        Object fromFile = nested(file, filePath);
        if (fromFile != null && StringUtils.hasText(String.valueOf(fromFile))) return true;
        return StringUtils.hasText(environment.getProperty(property));
    }

    private List<AgentType> enumList(Map<String, Object> file, String filePath, String property) {
        Object fromFile = nested(file, filePath);
        List<String> raw = new ArrayList<>();
        if (fromFile instanceof Collection<?> collection) {
            collection.forEach(value -> raw.add(String.valueOf(value)));
        } else {
            String configured = environment.getProperty(property, "");
            if (StringUtils.hasText(configured)) raw.addAll(Arrays.stream(configured.split(",")).map(String::trim).toList());
        }
        List<AgentType> result = new ArrayList<>();
        for (String value : raw) {
            try { result.add(AgentType.valueOf(value)); } catch (Exception ignored) {}
        }
        return List.copyOf(result);
    }

    public record UpdateLocalSettingsRequest(
            String databaseUrl,
            String databaseUsername,
            String databasePassword,
            String openAiApiKey,
            String openAiBaseUrl,
            String geminiApiKey,
            String geminiBaseUrl,
            Boolean githubPublishEnabled,
            String githubToken,
            String githubBaseBranch,
            Boolean trelloEnabled,
            String trelloApiKey,
            String trelloToken,
            Boolean mcpEnabled,
            Boolean codexEnabled,
            String codexBinary,
            String codexModel,
            Set<AgentType> codexRoles,
            Map<String, RouteRequest> routes
    ) {}

    public record RouteRequest(String provider, String model) {}
    public record RouteView(String provider, String model) {}

    public record LocalSettingsView(
            boolean localProfile,
            String configPath,
            boolean configFileExists,
            boolean restartRequired,
            String databaseUrl,
            String databaseUsername,
            boolean databasePasswordConfigured,
            String openAiBaseUrl,
            boolean openAiApiKeyConfigured,
            String geminiBaseUrl,
            boolean geminiApiKeyConfigured,
            boolean githubPublishEnabled,
            boolean githubTokenConfigured,
            String githubBaseBranch,
            boolean trelloEnabled,
            boolean trelloApiKeyConfigured,
            boolean trelloTokenConfigured,
            boolean mcpEnabled,
            boolean codexEnabled,
            String codexBinary,
            String codexModel,
            List<AgentType> codexRoles,
            Map<String, RouteView> routes
    ) {}
}
