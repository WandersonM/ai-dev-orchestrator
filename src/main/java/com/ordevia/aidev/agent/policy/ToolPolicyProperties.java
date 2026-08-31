package com.ordevia.aidev.agent.policy;

import com.ordevia.aidev.agent.domain.AgentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "aidev.tool-policy")
public class ToolPolicyProperties {
    private Effect defaultEffect = Effect.DENY;
    private Map<AgentType, Rule> policies = new EnumMap<>(AgentType.class);

    public Effect getDefaultEffect() { return defaultEffect; }
    public void setDefaultEffect(Effect defaultEffect) { this.defaultEffect = defaultEffect; }
    public Map<AgentType, Rule> getPolicies() { return policies; }
    public void setPolicies(Map<AgentType, Rule> policies) { this.policies = policies; }

    public enum Effect { ALLOW, DENY }

    public static class Rule {
        private List<String> allow = new ArrayList<>();
        private List<String> deny = new ArrayList<>();

        public List<String> getAllow() { return allow; }
        public void setAllow(List<String> allow) { this.allow = allow; }
        public List<String> getDeny() { return deny; }
        public void setDeny(List<String> deny) { this.deny = deny; }
    }
}
