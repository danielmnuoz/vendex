package com.vendex.auth.grpc;

import com.nimbusds.jose.jwk.JWK;
import com.vendex.auth.domain.Role;
import com.vendex.auth.domain.User;
import com.vendex.auth.jwt.JwtService;
import com.vendex.auth.jwt.SigningKeyService;
import com.vendex.auth.service.AuthService;
import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.auth.v1.GetJWKSRequest;
import com.vendex.auth.v1.GetJWKSResponse;
import com.vendex.auth.v1.GetVendorProfileRequest;
import com.vendex.auth.v1.GetVendorProfileResponse;
import com.vendex.auth.v1.Jwk;
import com.vendex.auth.v1.LoginRequest;
import com.vendex.auth.v1.LoginResponse;
import com.vendex.auth.v1.RefreshTokenRequest;
import com.vendex.auth.v1.RefreshTokenResponse;
import com.vendex.auth.v1.RegisterRequest;
import com.vendex.auth.v1.RegisterResponse;
import com.vendex.auth.v1.UpdateProfileRequest;
import com.vendex.auth.v1.UpdateProfileResponse;
import com.vendex.auth.v1.ValidateTokenRequest;
import com.vendex.auth.v1.ValidateTokenResponse;
import com.vendex.auth.v1.VendorProfile;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC surface for AuthService. Pure adapter: every method converts proto
 * request → domain call → proto response, with exceptions routed through
 * {@link ErrorMapper}.
 */
@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AuthGrpcService.class);

    private final AuthService authService;
    private final SigningKeyService signingKeys;

    public AuthGrpcService(AuthService authService, SigningKeyService signingKeys) {
        this.authService = authService;
        this.signingKeys = signingKeys;
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> obs) {
        try {
            UUID id = authService.register(
                    request.getEmail(),
                    request.getPassword(),
                    fromProto(request.getRole()),
                    request.getShopName(),
                    request.getCity(),
                    request.getState()
            );
            obs.onNext(RegisterResponse.newBuilder().setUserId(id.toString()).build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> obs) {
        try {
            var result = authService.login(request.getEmail(), request.getPassword());
            obs.onNext(LoginResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .setAccessTokenExpiresAtEpochSeconds(result.accessTokenExpiresAt().getEpochSecond())
                    .build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<RefreshTokenResponse> obs) {
        try {
            var result = authService.refresh(request.getRefreshToken());
            obs.onNext(RefreshTokenResponse.newBuilder()
                    .setAccessToken(result.accessToken())
                    .setRefreshToken(result.refreshToken())
                    .setAccessTokenExpiresAtEpochSeconds(result.accessTokenExpiresAt().getEpochSecond())
                    .build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> obs) {
        try {
            var claimsOpt = authService.validateAccessToken(request.getAccessToken());
            if (claimsOpt.isEmpty()) {
                obs.onNext(ValidateTokenResponse.newBuilder().setValid(false).build());
            } else {
                JwtService.Claims c = claimsOpt.get();
                obs.onNext(ValidateTokenResponse.newBuilder()
                        .setValid(true)
                        .setUserId(c.userId().toString())
                        .setRole(toProto(c.role()))
                        .setExpiresAtEpochSeconds(c.expiresAt().getEpochSecond())
                        .build());
            }
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getJWKS(GetJWKSRequest request, StreamObserver<GetJWKSResponse> obs) {
        try {
            var builder = GetJWKSResponse.newBuilder();
            for (JWK jwk : signingKeys.buildJwks().getKeys()) {
                var json = jwk.toJSONObject();
                builder.addKeys(Jwk.newBuilder()
                        .setKid(stringValue(json, "kid"))
                        .setAlg(stringValue(json, "alg"))
                        .setKty(stringValue(json, "kty"))
                        .setUse(stringValue(json, "use"))
                        .setN(stringValue(json, "n"))
                        .setE(stringValue(json, "e"))
                        .build());
            }
            obs.onNext(builder.build());
            obs.onCompleted();
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void getVendorProfile(GetVendorProfileRequest request, StreamObserver<GetVendorProfileResponse> obs) {
        try {
            AuthContext.requireSubject();   // any authenticated caller can read a vendor's public profile
            UUID id = UUID.fromString(request.getUserId());
            User user = authService.getProfile(id);
            obs.onNext(GetVendorProfileResponse.newBuilder()
                    .setProfile(toProto(user))
                    .build());
            obs.onCompleted();
        } catch (IllegalArgumentException e) {
            obs.onError(Status.INVALID_ARGUMENT.withDescription("invalid user_id").asRuntimeException());
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> obs) {
        try {
            UUID subject = AuthContext.requireSubject();
            UUID target = UUID.fromString(request.getUserId());
            if (!subject.equals(target)) {
                throw Status.PERMISSION_DENIED
                        .withDescription("cannot update another user's profile")
                        .asRuntimeException();
            }
            User updated = authService.updateProfile(
                    target,
                    request.getShopName(),
                    request.getCity(),
                    request.getState()
            );
            obs.onNext(UpdateProfileResponse.newBuilder()
                    .setProfile(toProto(updated))
                    .build());
            obs.onCompleted();
        } catch (IllegalArgumentException e) {
            obs.onError(Status.INVALID_ARGUMENT.withDescription("invalid user_id").asRuntimeException());
        } catch (Exception e) {
            obs.onError(ErrorMapper.map(e));
        }
    }

    // --- helpers ---

    private static Role fromProto(com.vendex.auth.v1.Role proto) {
        return switch (proto) {
            case ROLE_VENDOR -> Role.VENDOR;
            case ROLE_ATTENDEE -> Role.ATTENDEE;
            case ROLE_ORGANIZER -> Role.ORGANIZER;
            default -> throw new IllegalArgumentException("role required");
        };
    }

    private static com.vendex.auth.v1.Role toProto(Role role) {
        return switch (role) {
            case VENDOR -> com.vendex.auth.v1.Role.ROLE_VENDOR;
            case ATTENDEE -> com.vendex.auth.v1.Role.ROLE_ATTENDEE;
            case ORGANIZER -> com.vendex.auth.v1.Role.ROLE_ORGANIZER;
        };
    }

    private static VendorProfile toProto(User u) {
        return VendorProfile.newBuilder()
                .setUserId(u.id().toString())
                .setEmail(u.email())
                .setRole(toProto(u.role()))
                .setShopName(u.shopName() == null ? "" : u.shopName())
                .setCity(u.city() == null ? "" : u.city())
                .setState(u.state() == null ? "" : u.state())
                .setCreatedAtEpochSeconds(u.createdAt().getEpochSecond())
                .build();
    }

    private static String stringValue(java.util.Map<String, Object> json, String key) {
        Object v = json.get(key);
        return v == null ? "" : v.toString();
    }
}
