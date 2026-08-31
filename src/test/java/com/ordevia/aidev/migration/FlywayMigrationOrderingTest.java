package com.ordevia.aidev.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationOrderingTest {
    private static final Pattern VERSION = Pattern.compile("^V([0-9]+(?:_[0-9]+)*)__.*\\.sql$");

    @Test
    void migrationVersionsMustBeUniqueAndNamedCorrectly() throws IOException {
        Path directory = Path.of("src/main/resources/db/migration");
        assertTrue(Files.isDirectory(directory), "Flyway migration directory is missing");

        Map<String, String> versions = new HashMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String filename = path.getFileName().toString();
                Matcher matcher = VERSION.matcher(filename);
                assertTrue(matcher.matches(), "Invalid Flyway migration filename: " + filename);
                String version = matcher.group(1);
                String previous = versions.putIfAbsent(version, filename);
                assertNull(previous, "Duplicate Flyway version V" + version + ": " + previous + " and " + filename);
            }
        }
    }
}
