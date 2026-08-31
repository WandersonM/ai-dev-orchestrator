package com.ordevia.aidev.workspace.application;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class CommandPolicy {
    private static final Set<String> ALLOWED = Set.of("git","mvn","./mvnw","gradle","./gradlew","npm","pnpm","grep","find","cat");
    public void validate(String executable) { if(!ALLOWED.contains(executable)) throw new SecurityException("Command not allowed: " + executable); }
}
