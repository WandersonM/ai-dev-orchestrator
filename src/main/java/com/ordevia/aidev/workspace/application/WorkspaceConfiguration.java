package com.ordevia.aidev.workspace.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CommandPolicyProperties.class)
public class WorkspaceConfiguration {}
