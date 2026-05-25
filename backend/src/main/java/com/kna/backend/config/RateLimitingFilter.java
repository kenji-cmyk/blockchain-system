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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int limit;
    private final long windowMillis;
    private final Map<String, RateWindow> windows = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${blockchain.rate-limit.enabled:true}") boolean enabled,
            @Value("${blockchain.rate-limit.expensive-limit:20}") int limit,
            @Value("${blockchain.rate-limit.window-ms:60000}") long windowMillis
    ) {
        this.enabled = enabled;
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1000, windowMillis);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || !isExpensive(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        long now = System.currentTimeMillis();
        RateWindow window = windows.compute(key, (ignored, existing) ->
                existing == null || now >= existing.resetAtMillis()
                        ? new RateWindow(now + windowMillis, new AtomicInteger(0))
                        : existing
        );

        if (window.count().incrementAndGet() > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"timestamp":"%s","status":429,"error":"Too Many Requests","message":"Rate limit exceeded","path":"%s"}
                    """.formatted(Instant.now(), request.getRequestURI()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExpensive(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/blocks")
                || path.equals("/api/transactions/mine")
                || path.equals("/api/peers/broadcast/transactions")
                || path.equals("/api/transactions/broadcast")
                || path.equals("/api/blocks/broadcast");
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr() + "|" + request.getRequestURI();
    }

    private record RateWindow(long resetAtMillis, AtomicInteger count) {
    }
}
