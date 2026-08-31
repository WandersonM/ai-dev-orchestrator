package com.ordevia.aidev.agent.policy;

import com.ordevia.aidev.agent.domain.AgentType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPolicyTest {

    @Test
    void denyMustWinOverAllow() {
        ToolPolicyProperties properties = properties(
                AgentType.BACKEND_DEVELOPER,
                List.of("mcp_*"),
                List.of("mcp_prod_*"));
        ToolPolicy policy = new ToolPolicy(properties);

        assertThat(policy.isAllowed(AgentType.BACKEND_DEVELOPER, "mcp_context7_search")).isTrue();
        assertThat(policy.isAllowed(AgentType.BACKEND_DEVELOPER, "mcp_prod_execute_sql")).isFalse();
    }

    @Test
    void defaultEffectIsDeny() {
        ToolPolicy policy = new ToolPolicy(new ToolPolicyProperties());
        assertThat(policy.isAllowed(AgentType.REVIEWER, "write_file")).isFalse();
    }

    @Test
    void requireAllowedMustRejectUnauthorizedTool() {
        ToolPolicyProperties properties = properties(
                AgentType.REVIEWER,
                List.of("read_file", "mcp_docs_*"),
                List.of("mcp_docs_delete_*"));
        ToolPolicy policy = new ToolPolicy(properties);

        assertThatThrownBy(() -> policy.requireAllowed(AgentType.REVIEWER, "write_file"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("write_file")
                .hasMessageContaining("REVIEWER");
    }

    private ToolPolicyProperties properties(AgentType type, List<String> allow, List<String> deny) {
        ToolPolicyProperties properties = new ToolPolicyProperties();
        properties.setDefaultEffect(ToolPolicyProperties.Effect.DENY);
        ToolPolicyProperties.Rule rule = new ToolPolicyProperties.Rule();
        rule.setAllow(allow);
        rule.setDeny(deny);
        Map<AgentType, ToolPolicyProperties.Rule> policies = new EnumMap<>(AgentType.class);
        policies.put(type, rule);
        properties.setPolicies(policies);
        return properties;
    }
}
