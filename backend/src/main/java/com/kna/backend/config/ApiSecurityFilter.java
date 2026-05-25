package com.kna.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Component
@Order(10)
public class ApiSecurityFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final String operatorToken;
    private final String readOnlyToken;
    private final long maxRequestBytes;

    public ApiSecurityFilter(
            @Value("${blockchain.security.enabled:true}") boolean enabled,
            @Value("${blockchain.security.operator-token:operator-token}") String operatorToken,
            @Value("${blockchain.security.read-only-token:read-only-token}") String readOnlyToken,
            @Value("${blockchain.security.max-request-bytes:65536}") long maxRequestBytes
    ) {
        this.enabled = enabled;
        this.operatorToken = operatorToken;
        this.readOnlyToken = readOnlyToken;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxRequestBytes) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds " + maxRequestBytes + " bytes", request);
            return;
        }

        if (!enabled || !requiresOperator(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Role role = resolveRole(request.getHeader("Authorization"));
        if (role == Role.NONE) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication is required", request);
            return;
        }
        if (role != Role.OPERATOR) {
            writeError(response, HttpStatus.FORBIDDEN, "Operator role is required", request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresOperator(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("PUT".equals(method) && "/api/chain/difficulty".equals(path)) {
            return true;
        }
        if ("POST".equals(method) && Set.of("/api/chain/reset", "/api/chain/tamper").contains(path)) {
            return true;
        }
        if ("POST".equals(method) && Set.of(
                "/api/blocks",
                "/api/transactions/mine",
                "/api/peers",
                "/api/peers/discover",
                "/api/peers/broadcast/transactions"
        ).contains(path)) {
            return true;
        }
        return ("DELETE".equals(method) || "POST".equals(method))
                && path.startsWith("/api/peers/");
    }

    private Role resolveRole(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Role.NONE;
        }
        String token = authorization.substring("Bearer ".length()).strip();
        if (operatorToken.equals(token)) {
            return Role.OPERATOR;
        }
        if (readOnlyToken.equals(token)) {
            return Role.READ_ONLY;
        }
        return Role.NONE;
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"error":"%s","message":"%s","path":"%s"}
                """.formatted(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        ));
    }

    private enum Role {
        NONE,
        READ_ONLY,
        OPERATOR
    }
}
