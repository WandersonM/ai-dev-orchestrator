package com.ordevia.aidev.workspace.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix="aidev.command-policy")
public record CommandPolicyProperties(List<String> allowedExecutables) {
    public CommandPolicyProperties {
        allowedExecutables = allowedExecutables == null || allowedExecutables.isEmpty()
                ? List.of("git","mvn","./mvnw","gradle","./gradlew","npm","pnpm","grep","find","cat")
                : List.copyOf(allowedExecutables);
    }
}
