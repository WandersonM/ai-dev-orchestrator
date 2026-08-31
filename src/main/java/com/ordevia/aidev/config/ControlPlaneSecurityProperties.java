package com.ordevia.aidev.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="aidev.security")
public record ControlPlaneSecurityProperties(String controlToken) {}
