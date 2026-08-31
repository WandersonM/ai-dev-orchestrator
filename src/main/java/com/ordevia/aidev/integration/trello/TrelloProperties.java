package com.ordevia.aidev.integration.trello;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aidev.trello")
public record TrelloProperties(
        boolean enabled,
        String apiKey,
        String token,
        String baseUrl,
        boolean pollingEnabled,
        long pollingIntervalMs
) {
    public TrelloProperties {
        if(baseUrl==null||baseUrl.isBlank())baseUrl="https://api.trello.com";
        if(pollingIntervalMs<=0)pollingIntervalMs=30000;
    }
}
