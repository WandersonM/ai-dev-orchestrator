package com.ordevia.aidev.llm.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix="aidev.llm-pricing")
public record LlmPricingProperties(Map<String,ModelPrice> models) {
    public LlmPricingProperties { if(models==null) models=Map.of(); }
    public record ModelPrice(BigDecimal inputPerMillion,BigDecimal outputPerMillion,BigDecimal cachedInputPerMillion) {}
}
