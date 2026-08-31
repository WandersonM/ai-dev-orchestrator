package com.ordevia.aidev.workspace.application;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CommandPolicy {
    private final Set<String> allowed;

    public CommandPolicy(CommandPolicyProperties properties) {
        this.allowed = properties.allowedExecutables().stream().map(String::trim).filter(s->!s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public void validate(String executable) {
        if (executable == null || executable.isBlank()) throw new SecurityException("Executable is required");
        if (executable.contains("\u0000") || executable.contains("\n") || executable.contains("\r")) throw new SecurityException("Invalid executable");
        String normalized = executable.replace('\\','/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) throw new SecurityException("Absolute executable paths are not allowed");
        if (normalized.contains("../")) throw new SecurityException("Executable path traversal is not allowed");
        if (!allowed.contains(normalized)) throw new SecurityException("Command not allowed: " + executable);
    }

    public Set<String> allowedExecutables(){return allowed;}
}
