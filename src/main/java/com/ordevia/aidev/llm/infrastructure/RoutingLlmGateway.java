package com.ordevia.aidev.llm.infrastructure;

import com.ordevia.aidev.llm.domain.*;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutingLlmGateway implements LlmGateway {
    private final Map<LlmProvider, ProviderLlmGateway> providers = new EnumMap<>(LlmProvider.class);
    private final LlmRoutingProperties properties;

    public RoutingLlmGateway(List<ProviderLlmGateway> gateways, LlmRoutingProperties properties) {
        gateways.forEach(g -> providers.put(g.provider(), g));
        this.properties = properties;
    }

    @Override
    public LlmResponse execute(LlmRequest request) {
        LlmRoutingProperties.Route route = route(request.task());
        return requiredProvider(route.provider()).execute(request, route.model());
    }

    @Override
    public LlmToolResponse executeTools(LlmToolRequest request) {
        LlmRoutingProperties.Route route = route(request.task());
        return requiredProvider(route.provider()).executeTools(request, route.model());
    }

    private LlmRoutingProperties.Route route(LlmTask task) {
        return switch (task) {
            case REFINEMENT -> properties.routes().refinement();
            case ARCHITECTURE -> properties.routes().architecture();
            case BACKEND_IMPLEMENTATION -> properties.routes().backend();
            case FRONTEND_IMPLEMENTATION -> properties.routes().frontend();
            case QA -> properties.routes().qa();
            case REVIEW -> properties.routes().review();
            case SECURITY_REVIEW -> properties.routes().security();
            case INTEGRATION -> properties.routes().integration();
            case RELEASE -> properties.routes().release();
            case DOMAIN_VALIDATION -> properties.routes().domainValidation();
        };
    }

    private ProviderLlmGateway requiredProvider(LlmProvider provider) {
        ProviderLlmGateway gateway = providers.get(provider);
        if (gateway == null) throw new IllegalStateException("LLM provider not configured: " + provider);
        return gateway;
    }
}
