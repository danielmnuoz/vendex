package com.vendex.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

public final class PublicRoutes {

    private static final Set<String> PUBLIC_POSTS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh");

    private PublicRoutes() {}

    public static boolean isPublic(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        if (request.getRequestURI().startsWith("/actuator/health")) {
            return true;
        }
        return "POST".equals(request.getMethod()) && PUBLIC_POSTS.contains(request.getRequestURI());
    }
}
