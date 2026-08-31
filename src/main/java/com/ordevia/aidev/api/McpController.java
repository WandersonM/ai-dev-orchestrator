package com.ordevia.aidev.api;

import com.ordevia.aidev.mcp.McpConnectionManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
public class McpController {
    private final McpConnectionManager manager;

    public McpController(McpConnectionManager manager) {
        this.manager = manager;
    }

    @GetMapping("/servers")
    public List<McpConnectionManager.ServerState> servers() {
        return manager.states();
    }

    @PostMapping("/servers/{name}/reconnect")
    public McpConnectionManager.ServerState reconnect(@PathVariable String name) {
        return manager.reconnect(name);
    }
}
