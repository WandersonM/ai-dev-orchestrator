package com.ordevia.aidev.agent.skill;

import com.ordevia.aidev.agent.domain.AgentType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Component
public class SkillRegistry {
    private static final int MAX_SKILLS = 100;
    private static final int MAX_BODY_CHARS = 20_000;
    private static final int MAX_CONTEXT_CHARS = 60_000;

    public List<AgentSkill> discover(Path taskRoot) {
        List<Path> roots = repositoryRoots(taskRoot);
        List<AgentSkill> result = new ArrayList<>();
        for (Path root : roots) {
            Path skills = root.resolve(".ai").resolve("skills");
            if (!Files.isDirectory(skills)) continue;
            try (Stream<Path> files = Files.walk(skills, 4)) {
                for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md")).sorted().toList()) {
                    if (result.size() >= MAX_SKILLS) return List.copyOf(result);
                    parse(file).ifPresent(result::add);
                }
            } catch (Exception ignored) {
            }
        }
        return List.copyOf(result);
    }

    public String contextFor(AgentType role, Path taskRoot, Collection<String> availableTools) {
        Set<String> tools = new LinkedHashSet<>(availableTools);
        StringBuilder out = new StringBuilder();
        for (AgentSkill skill : discover(taskRoot)) {
            if (!skill.roles().isEmpty() && !skill.roles().contains(role)) continue;
            if (!requirementsSatisfied(skill.requiredTools(), tools)) continue;
            if (out.length() >= MAX_CONTEXT_CHARS) break;
            String entry = "\n## Skill: " + skill.name() + "\n" +
                    (skill.description() == null ? "" : skill.description() + "\n") +
                    "Source: " + skill.source() + "\n\n" + skill.body() + "\n";
            int remaining = MAX_CONTEXT_CHARS - out.length();
            out.append(entry, 0, Math.min(entry.length(), remaining));
        }
        return out.toString();
    }

    private Optional<AgentSkill> parse(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.length() > MAX_BODY_CHARS + 4000) raw = raw.substring(0, MAX_BODY_CHARS + 4000);
            Map<String, String> meta = new LinkedHashMap<>();
            String body = raw;
            if (raw.startsWith("---")) {
                int end = raw.indexOf("\n---", 3);
                if (end > 0) {
                    String header = raw.substring(3, end);
                    for (String line : header.split("\\R")) {
                        int idx = line.indexOf(':');
                        if (idx > 0) meta.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                    }
                    body = raw.substring(Math.min(raw.length(), end + 4)).trim();
                }
            }
            String name = meta.getOrDefault("name", file.getParent().getFileName().toString());
            Set<AgentType> roles = new LinkedHashSet<>();
            for (String token : csv(meta.get("roles"))) {
                try { roles.add(AgentType.valueOf(token.toUpperCase(Locale.ROOT))); } catch (Exception ignored) {}
            }
            Set<String> required = new LinkedHashSet<>(csv(meta.get("requiresTools")));
            if (body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS) + "\n...[truncated]";
            return Optional.of(new AgentSkill(name, meta.get("description"), Set.copyOf(roles), Set.copyOf(required), file, body));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean requirementsSatisfied(Set<String> required, Set<String> available) {
        for (String pattern : required) {
            boolean matched = available.stream().anyMatch(tool -> wildcard(pattern, tool));
            if (!matched) return false;
        }
        return true;
    }

    private boolean wildcard(String pattern, String value) {
        String regex = "^" + java.util.regex.Pattern.quote(pattern).replace("*", "\\E.*\\Q") + "$";
        return value.matches(regex);
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private List<Path> repositoryRoots(Path taskRoot) {
        Path root = taskRoot.toAbsolutePath().normalize();
        if (Files.exists(root.resolve(".git"))) return List.of(root);
        try (Stream<Path> children = Files.list(root)) {
            List<Path> repos = children.filter(Files::isDirectory).filter(p -> Files.exists(p.resolve(".git"))).toList();
            return repos.isEmpty() ? List.of(root) : repos;
        } catch (Exception e) {
            return List.of(root);
        }
    }
}
