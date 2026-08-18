package com.vendex.gateway.ratelimit;

import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.web.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class EdgeRateLimitFilter extends OncePerRequestFilter {

    private final RedisSlidingWindowRateLimiter limiter;
    private final GatewayProperties properties;
    private final ErrorResponseWriter errors;

    public EdgeRateLimitFilter(
            RedisSlidingWindowRateLimiter limiter,
            GatewayProperties properties,
            ErrorResponseWriter errors) {
        this.limiter = limiter;
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        int limit = properties.rateLimit().edgeRequests();
        try {
            var decision = limiter.evaluate(
                    "edge:" + request.getRemoteAddr(),
                    limit,
                    properties.rateLimit().window());
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
                errors.write(request, response, 429, "EDGE_RATE_LIMITED", "Too many requests from this network client");
                return;
            }
            filterChain.doFilter(request, response);
        } catch (DataAccessException | IllegalStateException error) {
            errors.write(request, response, 503, "RATE_LIMIT_UNAVAILABLE", "Request rate limiting is temporarily unavailable");
        }
    }
}
