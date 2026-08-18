package com.vendex.gateway.auth;

import com.vendex.gateway.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class GatewayAccessToken {

    public static final String ATTRIBUTE = GatewayAccessToken.class.getName();

    private GatewayAccessToken() {}

    public static String require(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String token && !token.isBlank()) {
            return token;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required");
    }
}
