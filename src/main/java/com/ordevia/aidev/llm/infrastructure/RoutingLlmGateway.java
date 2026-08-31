package com.ordevia.aidev.llm.infrastructure;

import com.ordevia.aidev.llm.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RoutingLlmGateway implements LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(RoutingLlmGateway.class);

    private final Map<LlmProvider, ProviderLlmGateway> providers = new EnumMap<>(LlmProvider.class);
    private final Map<LlmProvider, CircuitState> circuits = new ConcurrentHashMap<>();
    private final LlmRoutingProperties properties;
    private final LlmResilienceProperties resilience;

    public RoutingLlmGateway(List<ProviderLlmGateway> gateways,
                             LlmRoutingProperties properties,
                             LlmResilienceProperties resilience) {
        gateways.forEach(g -> providers.put(g.provider(), g));
        this.properties = properties;
        this.resilience = resilience;
        for (LlmProvider provider : LlmProvider.values()) circuits.put(provider, new CircuitState());
    }

    @Override
    public LlmResponse execute(LlmRequest request) {
        LlmRoutingProperties.Route route = route(request.task());
        LlmProvider primary = route.provider();
        if (circuitOpen(primary)) return fallbackText(request, primary, null);
        try {
            LlmResponse response = requiredProvider(primary).execute(request, route.model());
            success(primary);
            return response;
        } catch (RuntimeException primaryFailure) {
            failure(primary);
            return fallbackText(request, primary, primaryFailure);
        }
    }

    @Override
    public LlmToolResponse executeTools(LlmToolRequest request) {
        LlmRoutingProperties.Route route = route(request.task());
        LlmProvider primary = route.provider();
        if (circuitOpen(primary)) return fallbackTools(request, primary, null);
        try {
            LlmToolResponse response = requiredProvider(primary).executeTools(request, route.model());
            success(primary);
            return response;
        } catch (RuntimeException primaryFailure) {
            failure(primary);
            return fallbackTools(request, primary, primaryFailure);
        }
    }

    private LlmResponse fallbackText(LlmRequest request, LlmProvider primary, RuntimeException primaryFailure) {
        if (!resilience.enabled()) throw originalOrCircuit(primary, primaryFailure);
        LlmProvider fallback = alternate(primary);
        try {
            log.warn("LLM provider {} unavailable; falling back to {}", primary, fallback);
            LlmResponse response = requiredProvider(fallback).execute(request, fallbackModel(fallback));
            success(fallback);
            return response;
        } catch (RuntimeException fallbackFailure) {
            failure(fallback);
            if (primaryFailure != null) fallbackFailure.addSuppressed(primaryFailure);
            throw fallbackFailure;
        }
    }

    private LlmToolResponse fallbackTools(LlmToolRequest request, LlmProvider primary, RuntimeException primaryFailure) {
        if (!resilience.enabled()) throw originalOrCircuit(primary, primaryFailure);
        LlmProvider fallback = alternate(primary);
        try {
            log.warn("LLM provider {} unavailable; falling back to {} for tool call", primary, fallback);
            LlmToolResponse response = requiredProvider(fallback).executeTools(request, fallbackModel(fallback));
            success(fallback);
            return response;
        } catch (RuntimeException fallbackFailure) {
            failure(fallback);
            if (primaryFailure != null) fallbackFailure.addSuppressed(primaryFailure);
            throw fallbackFailure;
        }
    }

    private RuntimeException originalOrCircuit(LlmProvider provider, RuntimeException failure) {
        return failure != null ? failure : new IllegalStateException("LLM circuit is open for provider " + provider);
    }

    private boolean circuitOpen(LlmProvider provider) {
        CircuitState state = circuits.get(provider);
        Instant until = state.openUntil;
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            state.failures.set(0);
            state.openUntil = null;
            return false;
        }
        return true;
    }

    private void failure(LlmProvider provider) {
        CircuitState state = circuits.get(provider);
        int count = state.failures.incrementAndGet();
        if (count >= resilience.failureThreshold()) {
            state.openUntil = Instant.now().plus(resilience.openDuration());
            log.warn("Opening LLM circuit for {} until {} after {} consecutive failures", provider, state.openUntil, count);
        }
    }

    private void success(LlmProvider provider) {
        CircuitState state = circuits.get(provider);
        state.failures.set(0);
        state.openUntil = null;
    }

    private LlmProvider alternate(LlmProvider provider) {
        return switch (provider) {
            case OPENAI -> LlmProvider.GEMINI;
            case GEMINI -> LlmProvider.OPENAI;
        };
    }

    private String fallbackModel(LlmProvider provider) {
        return switch (provider) {
            case OPENAI -> resilience.fallbackOpenaiModel();
            case GEMINI -> resilience.fallbackGeminiModel();
        };
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

    private static final class CircuitState {
        private final AtomicInteger failures = new AtomicInteger();
        private volatile Instant openUntil;
    }
}
