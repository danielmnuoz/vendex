package com.vendex.auth.grpc;

import com.vendex.auth.service.AuthService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * Parses the {@code authorization} metadata as a Bearer token and, if valid,
 * exposes the caller's identity via {@link AuthContext}. Calls without a
 * token (or with an invalid one) proceed with no context populated — methods
 * that need auth call {@link AuthContext#requireSubject()} to enforce.
 */
@GrpcGlobalServerInterceptor
public class AuthInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        Context ctx = Context.current();
        String token = stripBearer(headers.get(AUTHORIZATION));
        if (token != null) {
            var claimsOpt = authService.validateAccessToken(token);
            if (claimsOpt.isPresent()) {
                var claims = claimsOpt.get();
                ctx = ctx
                        .withValue(AuthContext.USER_ID, claims.userId())
                        .withValue(AuthContext.ROLE, claims.role());
            }
        }
        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Strips the Bearer scheme. Case-insensitive on the scheme name; rejects
     * malformed headers ("Bearer Bearer xyz", "Basic xxx", empty credentials).
     */
    static String stripBearer(String header) {
        if (header == null || header.isBlank()) return null;
        int sp = header.indexOf(' ');
        if (sp <= 0) return null;
        String scheme = header.substring(0, sp);
        if (!"Bearer".equalsIgnoreCase(scheme)) return null;
        String credentials = header.substring(sp + 1).strip();
        if (credentials.isEmpty()) return null;
        // Reject anything that itself looks like another auth header.
        if (credentials.contains(" ")) return null;
        return credentials;
    }
}
