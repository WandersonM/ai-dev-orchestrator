package com.ordevia.aidev.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ControlPlaneAuthFilter extends OncePerRequestFilter {
    private final ControlPlaneSecurityProperties properties;

    public ControlPlaneAuthFilter(ControlPlaneSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String token = properties.controlToken();
        if (!StringUtils.hasText(token)) return true;
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;
        return path.equals("/api/integrations/trello/webhook") || path.equals("/api/integrations/github/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String expected = "Bearer " + properties.controlToken();
        String actual = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean valid = actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"control_plane_authentication_required\"}");
            return;
        }
        filterChain.doFilter(request,response);
    }
}
