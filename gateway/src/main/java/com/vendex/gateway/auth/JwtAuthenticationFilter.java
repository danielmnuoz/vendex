package com.vendex.gateway.auth;

import com.vendex.gateway.web.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final GatewayJwtValidator validator;
    private final ErrorResponseWriter errors;

    public JwtAuthenticationFilter(GatewayJwtValidator validator, ErrorResponseWriter errors) {
        this.validator = validator;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicRoutes.isPublic(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)
                || authorization.length() == BEARER.length()) {
            errors.write(request, response, 401, "AUTHENTICATION_REQUIRED", "A bearer access token is required");
            return;
        }
        if (authorization.length() > 4096) {
            errors.write(request, response, 401, "INVALID_ACCESS_TOKEN", "The access token is invalid or expired");
            return;
        }
        try {
            String accessToken = authorization.substring(BEARER.length()).trim();
            GatewayPrincipal principal = validator.validate(accessToken);
            request.setAttribute(GatewayPrincipal.ATTRIBUTE, principal);
            request.setAttribute(GatewayAccessToken.ATTRIBUTE, accessToken);
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException error) {
            errors.write(request, response, 401, "INVALID_ACCESS_TOKEN", error.getMessage());
        } catch (JwksUnavailableException error) {
            errors.write(request, response, 503, "JWKS_UNAVAILABLE", error.getMessage());
        }
    }
}
