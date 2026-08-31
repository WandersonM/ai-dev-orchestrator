package com.ordevia.aidev.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordevia.aidev.agent.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);

    private final McpProperties properties;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper;
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final Map<String, ServerState> states = new ConcurrentHashMap<>();
    private final Map<String, List<String>> registeredTools = new ConcurrentHashMap<>();

    public McpConnectionManager(McpProperties properties, ToolRegistry toolRegistry, ObjectMapper mapper) {
        this.properties = properties;
        this.toolRegistry = toolRegistry;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConfiguredServers() {
        if (!properties.isEnabled()) {
            log.info("MCP integration is disabled");
            return;
        }
        properties.getServers().forEach((name, config) -> {
            if (config.isEnabled()) connect(name, config);
            else states.put(name, new ServerState(name, "DISABLED", 0, null));
        });
    }

    public synchronized ServerState reconnect(String name) {
        McpProperties.Server config = properties.getServers().get(name);
        if (config == null) throw new IllegalArgumentException("Unknown MCP server: " + name);
        disconnect(name);
        return connect(name, config);
    }

    public List<ServerState> states() {
        List<ServerState> result = new ArrayList<>(states.values());
        result.sort(java.util.Comparator.comparing(ServerState::name));
        return result;
    }

    private ServerState connect(String name, McpProperties.Server config) {
        try {
            validate(name, config);
            McpClientTransport transport = createTransport(config);
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(properties.getRequestTimeout())
                    .build();
            client.initialize();

            McpSchema.ListToolsResult result = client.listTools();
            List<String> names = new ArrayList<>();
            for (McpSchema.Tool remoteTool : result.tools()) {
                if (!config.acceptsTool(remoteTool.name())) continue;
                McpAgentTool tool = new McpAgentTool(name, remoteTool, client, mapper);
                toolRegistry.register(tool);
                names.add(tool.name());
            }

            clients.put(name, client);
            registeredTools.put(name, names);
            ServerState state = new ServerState(name, "CONNECTED", names.size(), null);
            states.put(name, state);
            log.info("MCP server '{}' connected with {} exposed tools", name, names.size());
            return state;
        } catch (Exception e) {
            ServerState state = new ServerState(name, "FAILED", 0, e.getMessage());
            states.put(name, state);
            log.warn("Unable to connect MCP server '{}': {}", name, e.getMessage());
            return state;
        }
    }

    private McpClientTransport createTransport(McpProperties.Server config) {
        return switch (config.getTransport()) {
            case STDIO -> {
                ServerParameters parameters = ServerParameters.builder(config.getCommand())
                        .args(config.getArgs())
                        .env(config.getEnv())
                        .build();
                yield new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
            }
            case STREAMABLE_HTTP -> {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
                config.getHeaders().forEach(requestBuilder::header);
                yield HttpClientStreamableHttpTransport.builder(config.getUrl())
                        .endpoint(config.getEndpoint())
                        .requestBuilder(requestBuilder)
                        .build();
            }
        };
    }

    private void validate(String name, McpProperties.Server config) {
        if (config.getTransport() == McpProperties.Transport.STDIO && !StringUtils.hasText(config.getCommand())) {
            throw new IllegalArgumentException("MCP server '" + name + "' requires command for STDIO transport");
        }
        if (config.getTransport() == McpProperties.Transport.STREAMABLE_HTTP && !StringUtils.hasText(config.getUrl())) {
            throw new IllegalArgumentException("MCP server '" + name + "' requires url for STREAMABLE_HTTP transport");
        }
    }

    private synchronized void disconnect(String name) {
        List<String> tools = registeredTools.remove(name);
        if (tools != null) tools.forEach(toolRegistry::unregister);
        McpSyncClient client = clients.remove(name);
        if (client != null) {
            try { client.closeGracefully(); }
            catch (Exception e) { log.debug("Error closing MCP server '{}': {}", name, e.getMessage()); }
        }
    }

    @PreDestroy
    public void close() {
        new ArrayList<>(clients.keySet()).forEach(this::disconnect);
    }

    public record ServerState(String name, String status, int toolCount, String error) {}
}
