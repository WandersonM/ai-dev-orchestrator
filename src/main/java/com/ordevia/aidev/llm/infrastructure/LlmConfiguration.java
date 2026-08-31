package com.ordevia.aidev.llm.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LlmRoutingProperties.class,LlmPricingProperties.class,LlmResilienceProperties.class})
public class LlmConfiguration {}
