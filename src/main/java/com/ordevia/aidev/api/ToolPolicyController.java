package com.ordevia.aidev.api;

import com.ordevia.aidev.agent.domain.AgentType;
import com.ordevia.aidev.agent.policy.ToolPolicy;
import com.ordevia.aidev.agent.tool.AgentTool;
import com.ordevia.aidev.agent.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/tool-policies")
public class ToolPolicyController {
    private final ToolRegistry registry;
    private final ToolPolicy policy;

    public ToolPolicyController(ToolRegistry registry, ToolPolicy policy) {
        this.registry = registry;
        this.policy = policy;
    }

    @GetMapping
    public List<AgentToolPolicyView> list() {
        return Arrays.stream(AgentType.values()).map(this::view).toList();
    }

    @GetMapping("/{agentType}")
    public AgentToolPolicyView get(@PathVariable AgentType agentType) {
        return view(agentType);
    }

    private AgentToolPolicyView view(AgentType agentType) {
        List<ToolAccessView> tools = registry.all().stream()
                .map(tool -> new ToolAccessView(tool.name(), tool.description(), policy.isAllowed(agentType, tool.name())))
                .toList();
        return new AgentToolPolicyView(agentType, tools);
    }

    public record AgentToolPolicyView(AgentType agentType, List<ToolAccessView> tools) {}
    public record ToolAccessView(String name, String description, boolean allowed) {}
}
