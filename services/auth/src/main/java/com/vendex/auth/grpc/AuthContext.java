package com.vendex.auth.grpc;

import com.vendex.auth.domain.Role;
import io.grpc.Context;
import io.grpc.Status;

import java.util.UUID;

/**
 * Per-request authentication context, propagated via gRPC's {@link Context}.
 * Populated by {@link AuthInterceptor} when a valid bearer token is present
 * on the inbound call, read by methods that require an authenticated caller.
 */
public final class AuthContext {

    static final Context.Key<UUID> USER_ID = Context.key("auth.user_id");
    static final Context.Key<Role> ROLE = Context.key("auth.role");

    private AuthContext() {}

    /** Returns the caller's user_id or throws UNAUTHENTICATED. */
    public static UUID requireSubject() {
        UUID id = USER_ID.get();
        if (id == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("missing or invalid bearer token")
                    .asRuntimeException();
        }
        return id;
    }
}
