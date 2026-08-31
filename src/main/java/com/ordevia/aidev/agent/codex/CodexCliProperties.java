package com.ordevia.aidev.agent.codex;

import com.ordevia.aidev.agent.domain.AgentType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "aidev.codex")
public class CodexCliProperties {
    private boolean enabled;
    private String binary = "codex";
    private String model;
    private Duration timeout = Duration.ofMinutes(20);
    private boolean ephemeral = true;
    private boolean ignoreUserConfig;
    private boolean stripApiKeyEnvironment = true;
    private Set<AgentType> roles = new LinkedHashSet<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBinary() { return binary; }
    public void setBinary(String binary) { this.binary = binary; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public boolean isEphemeral() { return ephemeral; }
    public void setEphemeral(boolean ephemeral) { this.ephemeral = ephemeral; }
    public boolean isIgnoreUserConfig() { return ignoreUserConfig; }
    public void setIgnoreUserConfig(boolean ignoreUserConfig) { this.ignoreUserConfig = ignoreUserConfig; }
    public boolean isStripApiKeyEnvironment() { return stripApiKeyEnvironment; }
    public void setStripApiKeyEnvironment(boolean stripApiKeyEnvironment) { this.stripApiKeyEnvironment = stripApiKeyEnvironment; }
    public Set<AgentType> getRoles() { return roles; }
    public void setRoles(Set<AgentType> roles) { this.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles); }
}
