package com.ordevia.aidev.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpPropertiesTest {

    @Test
    void shouldAllowAllToolsWhenNoIncludeFilterExists() {
        McpProperties.Server server = new McpProperties.Server();
        assertTrue(server.acceptsTool("read_docs"));
    }

    @Test
    void shouldHonorIncludeAndExcludeFilters() {
        McpProperties.Server server = new McpProperties.Server();
        server.setIncludeTools(List.of("read_docs", "search_docs"));
        server.setExcludeTools(List.of("search_docs"));

        assertTrue(server.acceptsTool("read_docs"));
        assertFalse(server.acceptsTool("search_docs"));
        assertFalse(server.acceptsTool("delete_everything"));
    }
}
