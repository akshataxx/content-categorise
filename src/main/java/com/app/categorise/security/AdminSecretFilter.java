package com.app.categorise.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AdminSecretFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminSecretFilter.class);
    private static final String HEADER = "X-Admin-Secret";

    @Value("${app.adminSecret:}")
    private String adminSecret;

    @PostConstruct
    public void warnIfMissing() {
        if (adminSecret == null || adminSecret.isBlank()) {
            log.warn("ADMIN_SECRET is not set — /api/admin/** endpoints are effectively disabled (all requests will be rejected)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (adminSecret == null || adminSecret.isBlank() || !adminSecret.equals(provided)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
