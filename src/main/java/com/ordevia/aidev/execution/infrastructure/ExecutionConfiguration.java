package com.ordevia.aidev.execution.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SelfHostedWorkerProperties.class)
public class ExecutionConfiguration {}
