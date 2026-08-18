package com.vendex.gateway.ratelimit;

import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.PublicRoutes;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.web.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisSlidingWindowRateLimiter limiter;
    private final GatewayProperties properties;
    private final ErrorResponseWriter errors;

    public RateLimitFilter(
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
        boolean publicRoute = PublicRoutes.isPublic(request);
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(GatewayPrincipal.ATTRIBUTE);
        String identity = publicRoute
                ? "public:" + request.getRemoteAddr()
                : "user:" + principal.userId();
        int limit = publicRoute
                ? properties.rateLimit().publicRequests()
                : properties.rateLimit().authenticatedRequests();

        try {
            var decision = limiter.evaluate(identity, limit, properties.rateLimit().window());
            response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
            if (!decision.allowed()) {
                long retrySeconds = Math.max(1, decision.retryAfter().toSeconds());
                response.setHeader("Retry-After", Long.toString(retrySeconds));
                errors.write(request, response, 429, "RATE_LIMITED", "Too many requests; retry later");
                return;
            }
            filterChain.doFilter(request, response);
        } catch (DataAccessException | IllegalStateException error) {
            errors.write(request, response, 503, "RATE_LIMIT_UNAVAILABLE", "Request rate limiting is temporarily unavailable");
        }
    }
}
