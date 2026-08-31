package com.ordevia.aidev.execution;

import com.ordevia.aidev.execution.application.SafeCommandLineParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SafeCommandLineParserTest {
    private final SafeCommandLineParser parser = new SafeCommandLineParser();

    @Test
    void parsesQuotedArgumentsWithoutUsingShell() {
        assertEquals(List.of("mvn", "-DskipTests=true", "-Dmessage=hello world", "test"),
                parser.parse("mvn -DskipTests=true \"-Dmessage=hello world\" test"));
    }

    @Test
    void rejectsShellCompositionAndSubstitution() {
        assertThrows(SecurityException.class, () -> parser.parse("mvn test && rm -rf target"));
        assertThrows(SecurityException.class, () -> parser.parse("mvn test | cat"));
        assertThrows(SecurityException.class, () -> parser.parse("mvn $(echo test)"));
    }

    @Test
    void rejectsMalformedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("mvn \"test"));
    }
}
