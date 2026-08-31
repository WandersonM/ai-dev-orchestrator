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
    public RoutingLlmGateway(List<ProviderLlmGateway> gateways, LlmRoutingProperties properties) { gateways.forEach(g -> providers.put(g.provider(), g)); this.properties = properties; }
    @Override public LlmResponse execute(LlmRequest request) {
        LlmRoutingProperties.Route route = switch (request.task()) { case REFINEMENT -> properties.routes().refinement(); case BACKEND_IMPLEMENTATION -> properties.routes().backend(); case REVIEW -> properties.routes().review(); };
        ProviderLlmGateway gateway = providers.get(route.provider());
        if (gateway == null) throw new IllegalStateException("LLM provider not configured: " + route.provider());
        return gateway.execute(request, route.model());
    }
}
