package com.vendex.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vendex.gateway.auth.GatewayJwtValidator;
import com.vendex.gateway.auth.JwtAuthenticationFilter;
import com.vendex.gateway.ratelimit.RateLimitFilter;
import com.vendex.gateway.ratelimit.EdgeRateLimitFilter;
import com.vendex.gateway.ratelimit.RedisSlidingWindowRateLimiter;
import com.vendex.gateway.web.CorrelationIdFilter;
import com.vendex.gateway.web.ErrorResponseWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class FilterConfig {

    @Bean
    ErrorResponseWriter errorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        return new ErrorResponseWriter(objectMapper, clock);
    }

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(
            GatewayJwtValidator validator,
            ErrorResponseWriter errors) {
        return new JwtAuthenticationFilter(validator, errors);
    }

    @Bean
    EdgeRateLimitFilter edgeRateLimitFilter(
            RedisSlidingWindowRateLimiter limiter,
            GatewayProperties properties,
            ErrorResponseWriter errors) {
        return new EdgeRateLimitFilter(limiter, properties, errors);
    }

    @Bean
    RateLimitFilter rateLimitFilter(
            RedisSlidingWindowRateLimiter limiter,
            GatewayProperties properties,
            ErrorResponseWriter errors) {
        return new RateLimitFilter(limiter, properties, errors);
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationFilterRegistration(CorrelationIdFilter filter) {
        return registration(filter, 0);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> authenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        return registration(filter, 10);
    }

    @Bean
    FilterRegistrationBean<EdgeRateLimitFilter> edgeRateLimitFilterRegistration(EdgeRateLimitFilter filter) {
        return registration(filter, 5);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return registration(filter, 20);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> registration(T filter, int order) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(order);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
