package com.vendex.gateway.api;

import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.auth.v1.GetVendorProfileRequest;
import com.vendex.auth.v1.LoginRequest;
import com.vendex.auth.v1.RefreshTokenRequest;
import com.vendex.auth.v1.RegisterRequest;
import com.vendex.auth.v1.Role;
import com.vendex.auth.v1.UpdateProfileRequest;
import com.vendex.auth.v1.VendorProfile;
import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.auth.GatewayAccessToken;
import com.vendex.gateway.config.GatewayProperties;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthServiceGrpc.AuthServiceBlockingStub auth;
    private final GatewayProperties properties;

    public AuthController(AuthServiceGrpc.AuthServiceBlockingStub auth, GatewayProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/auth/register")
    ResponseEntity<RegisteredUser> register(@Valid @RequestBody RegisterBody body) {
        var response = stub().register(RegisterRequest.newBuilder()
                .setEmail(body.email())
                .setPassword(body.password())
                .setRole(Role.ROLE_VENDOR)
                .setShopName(body.shopName())
                .setCity(nullToEmpty(body.city()))
                .setState(nullToEmpty(body.state()))
                .build());
        return ResponseEntity.created(URI.create("/api/v1/profile"))
                .body(new RegisteredUser(response.getUserId()));
    }

    @PostMapping("/auth/login")
    TokenPair login(@Valid @RequestBody LoginBody body) {
        var response = stub().login(LoginRequest.newBuilder()
                .setEmail(body.email())
                .setPassword(body.password())
                .build());
        return new TokenPair(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getAccessTokenExpiresAtEpochSeconds());
    }

    @PostMapping("/auth/refresh")
    TokenPair refresh(@Valid @RequestBody RefreshBody body) {
        var response = stub().refreshToken(RefreshTokenRequest.newBuilder()
                .setRefreshToken(body.refreshToken())
                .build());
        return new TokenPair(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getAccessTokenExpiresAtEpochSeconds());
    }

    @GetMapping("/profile")
    ProfileResponse profile(HttpServletRequest request) {
        GatewayPrincipal principal = vendor(request);
        var response = authenticatedStub(request).getVendorProfile(GetVendorProfileRequest.newBuilder()
                .setUserId(principal.userId().toString())
                .build());
        return map(response.getProfile());
    }

    @PatchMapping("/profile")
    ProfileResponse updateProfile(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileBody body) {
        GatewayPrincipal principal = vendor(request);
        var response = authenticatedStub(request).updateProfile(UpdateProfileRequest.newBuilder()
                .setUserId(principal.userId().toString())
                .setShopName(nullToEmpty(body.shopName()))
                .setCity(nullToEmpty(body.city()))
                .setState(nullToEmpty(body.state()))
                .build());
        return map(response.getProfile());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private AuthServiceGrpc.AuthServiceBlockingStub stub() {
        return auth.withDeadlineAfter(properties.grpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private AuthServiceGrpc.AuthServiceBlockingStub authenticatedStub(HttpServletRequest request) {
        Metadata headers = new Metadata();
        headers.put(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + GatewayAccessToken.require(request));
        return stub().withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static ProfileResponse map(VendorProfile profile) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getEmail(),
                roleName(profile.getRole()),
                profile.getShopName(),
                profile.getCity(),
                profile.getState(),
                profile.getCreatedAtEpochSeconds());
    }

    private static String roleName(Role role) {
        return switch (role) {
            case ROLE_VENDOR -> "vendor";
            case ROLE_ATTENDEE -> "attendee";
            case ROLE_ORGANIZER -> "organizer";
            default -> "unspecified";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record RegisterBody(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String shopName,
            @Size(max = 120) String city,
            @Size(max = 64) String state) {}

    public record RegisteredUser(String userId) {}

    public record LoginBody(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record RefreshBody(@NotBlank @Size(max = 512) String refreshToken) {}

    public record TokenPair(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresAtEpochSeconds) {}

    public record UpdateProfileBody(
            @Size(max = 120) String shopName,
            @Size(max = 120) String city,
            @Size(max = 64) String state) {}

    public record ProfileResponse(
            String userId,
            String email,
            String role,
            String shopName,
            String city,
            String state,
            long createdAtEpochSeconds) {}
}
