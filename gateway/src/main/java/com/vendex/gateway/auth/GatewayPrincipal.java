package com.vendex.gateway.auth;

import com.vendex.gateway.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

public record GatewayPrincipal(UUID userId, GatewayRole role, Instant expiresAt) {

    public static final String ATTRIBUTE = GatewayPrincipal.class.getName();

    public static GatewayPrincipal require(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof GatewayPrincipal principal) {
            return principal;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required");
    }

    public void requireRole(GatewayRole required) {
        if (role != required) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "This route requires the "
                    + required.name().toLowerCase() + " role");
        }
    }
}
