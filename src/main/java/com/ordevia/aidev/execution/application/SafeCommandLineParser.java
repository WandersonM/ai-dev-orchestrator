package com.ordevia.aidev.execution.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SafeCommandLineParser {
    public List<String> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote) quote = 0; else current.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                flush(tokens, current);
                continue;
            }
            if ("|;&><`$".indexOf(c) >= 0) {
                throw new SecurityException("Shell metacharacters are not allowed in environment hooks");
            }
            current.append(c);
        }
        if (quote != 0 || escape) throw new IllegalArgumentException("Malformed setup command");
        flush(tokens, current);
        if (tokens.isEmpty()) throw new IllegalArgumentException("Setup command cannot be empty");
        return List.copyOf(tokens);
    }

    private void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
