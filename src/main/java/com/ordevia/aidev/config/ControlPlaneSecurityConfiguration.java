package com.ordevia.aidev.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ControlPlaneSecurityProperties.class)
public class ControlPlaneSecurityConfiguration {}
