package com.vendex.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import com.vendex.auth.v1.Role;
import com.vendex.auth.v1.UpdateProfileRequest;
import com.vendex.auth.v1.UpdateProfileResponse;
import com.vendex.auth.v1.VendorProfile;
import com.vendex.cards.v1.Card;
import com.vendex.cards.v1.CardCatalogServiceGrpc;
import com.vendex.cards.v1.GetCardByIdRequest;
import com.vendex.cards.v1.GetCardByIdResponse;
import com.vendex.cards.v1.ListSetsRequest;
import com.vendex.cards.v1.ListSetsResponse;
import com.vendex.cards.v1.SearchCardsRequest;
import com.vendex.cards.v1.SearchCardsResponse;
import com.vendex.cards.v1.SetSummary;
import com.vendex.gateway.ratelimit.RedisSlidingWindowRateLimiter;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationIT {

    private static final UUID USER_ID = UUID.fromString("5e93928a-89cc-4e16-bf22-9a62db90fd21");
    private static final String KID = "19efe176-3e39-43cc-a895-7efeca391903";
    private static final RSAKey SIGNING_KEY = signingKey();
    private static final AtomicInteger JWKS_CALLS = new AtomicInteger();
    private static final Context.Key<String> FORWARDED_AUTHORIZATION = Context.key("forwarded-authorization");
    private static final Server GRPC = grpcServer();

    static final GenericContainer<?> REDIS = redis();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gateway.services.auth-target", () -> "localhost:" + GRPC.getPort());
        registry.add("gateway.services.card-catalog-target", () -> "localhost:" + GRPC.getPort());
        registry.add("gateway.jwt.cache-ttl", () -> "5m");
        registry.add("gateway.rate-limit.public-requests", () -> "100");
        registry.add("gateway.rate-limit.authenticated-requests", () -> "100");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RedisSlidingWindowRateLimiter rateLimiter;

    @AfterAll
    static void stopGrpc() {
        GRPC.shutdownNow();
    }

    @Test
    void enforcesTheRestTrustBoundaryAndTranslatesAuthCardsAndProfile() throws Exception {
        ResponseEntity<String> registered = rest.postForEntity(url("/api/v1/auth/register"), Map.of(
                "email", "vendor@example.com",
                "password", "correct-horse",
                "shopName", "Example Cards",
                "city", "Boston",
                "state", "MA"), String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(registered.getBody()).get("userId").asText()).isEqualTo(USER_ID.toString());

        ResponseEntity<String> unauthenticated = rest.getForEntity(url("/api/v1/cards/search?query=pikachu"), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getHeaders().getFirst("X-Request-ID")).isNotBlank();
        assertThat(objectMapper.readTree(unauthenticated.getBody()).get("code").asText())
                .isEqualTo("AUTHENTICATION_REQUIRED");

        JsonNode login = objectMapper.readTree(rest.postForObject(url("/api/v1/auth/login"), Map.of(
                "email", "vendor@example.com",
                "password", "correct-horse"), String.class));
        String accessToken = login.get("accessToken").asText();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("X-Request-ID", "gateway-it-request");

        ResponseEntity<String> cards = rest.exchange(
                url("/api/v1/cards/search?query=pikachu&pageSize=10"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(cards.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cards.getHeaders().getFirst("X-Request-ID")).isEqualTo("gateway-it-request");
        assertThat(cards.getHeaders().getFirst("X-RateLimit-Remaining")).isNotBlank();
        JsonNode cardsJson = objectMapper.readTree(cards.getBody());
        assertThat(cardsJson.get("cards").get(0).get("name").asText()).isEqualTo("Pikachu");
        assertThat(JWKS_CALLS).hasValue(1);

        ResponseEntity<String> profile = rest.exchange(
                url("/api/v1/profile"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(profile.getBody()).get("userId").asText()).isEqualTo(USER_ID.toString());
        assertThat(JWKS_CALLS).hasValue(1);

        ResponseEntity<String> notFound = rest.exchange(
                url("/api/v1/cards/missing"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(objectMapper.readTree(notFound.getBody()).get("code").asText())
                .isEqualTo("DOWNSTREAM_NOT_FOUND");

        ResponseEntity<String> unknownRoute = rest.exchange(
                url("/api/v1/does-not-exist"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(unknownRoute.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode unknownRouteJson = objectMapper.readTree(unknownRoute.getBody());
        assertThat(unknownRouteJson.get("code").asText()).isEqualTo("ROUTE_NOT_FOUND");
        assertThat(unknownRouteJson.get("path").asText()).isEqualTo("/api/v1/does-not-exist");
        assertThat(unknownRouteJson.get("requestId").asText()).isEqualTo("gateway-it-request");
    }

    @Test
    void usesAnAtomicRedisSlidingWindow() {
        String identity = "it:" + UUID.randomUUID();

        assertThat(rateLimiter.evaluate(identity, 2, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(rateLimiter.evaluate(identity, 2, Duration.ofMinutes(1)).allowed()).isTrue();
        var denied = rateLimiter.evaluate(identity, 2, Duration.ofMinutes(1));

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfter()).isPositive();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Server grpcServer() {
        try {
            Metadata.Key<String> authorization = Metadata.Key.of(
                    "authorization", Metadata.ASCII_STRING_MARSHALLER);
            ServerInterceptor captureAuthorization = new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call,
                        Metadata headers,
                        ServerCallHandler<ReqT, RespT> next) {
                    return Contexts.interceptCall(
                            Context.current().withValue(FORWARDED_AUTHORIZATION, headers.get(authorization)),
                            call,
                            headers,
                            next);
                }
            };
            return ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(new FakeAuthService(), captureAuthorization))
                    .addService(new FakeCardService())
                    .build()
                    .start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static GenericContainer<?> redis() {
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        redis.start();
        return redis;
    }

    private static RSAKey signingKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KID).generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String accessToken() {
        try {
            Instant now = Instant.now();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).type(JOSEObjectType.JWT).build(),
                    new JWTClaimsSet.Builder()
                            .subject(USER_ID.toString())
                            .issuer("https://auth.vendex.local")
                            .issueTime(Date.from(now.minusSeconds(1)))
                            .expirationTime(Date.from(now.plusSeconds(900)))
                            .claim("role", "VENDOR")
                            .build());
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static VendorProfile profile(String shopName, String city, String state) {
        return VendorProfile.newBuilder()
                .setUserId(USER_ID.toString())
                .setEmail("vendor@example.com")
                .setRole(Role.ROLE_VENDOR)
                .setShopName(shopName)
                .setCity(city)
                .setState(state)
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .build();
    }

    private static final class FakeAuthService extends AuthServiceGrpc.AuthServiceImplBase {
        @Override
        public void register(RegisterRequest request, StreamObserver<RegisterResponse> response) {
            assertThat(request.getRole()).isEqualTo(Role.ROLE_VENDOR);
            response.onNext(RegisterResponse.newBuilder().setUserId(USER_ID.toString()).build());
            response.onCompleted();
        }

        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> response) {
            response.onNext(LoginResponse.newBuilder()
                    .setAccessToken(accessToken())
                    .setRefreshToken("refresh-token")
                    .setAccessTokenExpiresAtEpochSeconds(Instant.now().plusSeconds(900).getEpochSecond())
                    .build());
            response.onCompleted();
        }

        @Override
        public void refreshToken(RefreshTokenRequest request, StreamObserver<RefreshTokenResponse> response) {
            response.onNext(RefreshTokenResponse.newBuilder()
                    .setAccessToken(accessToken())
                    .setRefreshToken("rotated-refresh-token")
                    .setAccessTokenExpiresAtEpochSeconds(Instant.now().plusSeconds(900).getEpochSecond())
                    .build());
            response.onCompleted();
        }

        @Override
        public void getJWKS(GetJWKSRequest request, StreamObserver<GetJWKSResponse> response) {
            JWKS_CALLS.incrementAndGet();
            response.onNext(GetJWKSResponse.newBuilder().addKeys(Jwk.newBuilder()
                    .setKid(KID)
                    .setAlg("RS256")
                    .setKty("RSA")
                    .setUse("sig")
                    .setN(SIGNING_KEY.getModulus().toString())
                    .setE(SIGNING_KEY.getPublicExponent().toString())
                    .build()).build());
            response.onCompleted();
        }

        @Override
        public void getVendorProfile(
                GetVendorProfileRequest request,
                StreamObserver<GetVendorProfileResponse> response) {
            String authorization = FORWARDED_AUTHORIZATION.get();
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                response.onError(Status.UNAUTHENTICATED.asRuntimeException());
                return;
            }
            assertThat(request.getUserId()).isEqualTo(USER_ID.toString());
            response.onNext(GetVendorProfileResponse.newBuilder()
                    .setProfile(profile("Example Cards", "Boston", "MA"))
                    .build());
            response.onCompleted();
        }

        @Override
        public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> response) {
            response.onNext(UpdateProfileResponse.newBuilder()
                    .setProfile(profile(request.getShopName(), request.getCity(), request.getState()))
                    .build());
            response.onCompleted();
        }
    }

    private static final class FakeCardService extends CardCatalogServiceGrpc.CardCatalogServiceImplBase {
        @Override
        public void searchCards(SearchCardsRequest request, StreamObserver<SearchCardsResponse> response) {
            response.onNext(SearchCardsResponse.newBuilder().addCards(card()).build());
            response.onCompleted();
        }

        @Override
        public void getCardById(GetCardByIdRequest request, StreamObserver<GetCardByIdResponse> response) {
            if ("missing".equals(request.getCardId())) {
                response.onError(Status.NOT_FOUND.withDescription("card not found").asRuntimeException());
                return;
            }
            response.onNext(GetCardByIdResponse.newBuilder().setCard(card()).build());
            response.onCompleted();
        }

        @Override
        public void listSets(ListSetsRequest request, StreamObserver<ListSetsResponse> response) {
            response.onNext(ListSetsResponse.newBuilder().addSets(SetSummary.newBuilder()
                    .setId("base1")
                    .setName("Base Set")
                    .setSeries("Base")
                    .setCardCount(102)
                    .build()).build());
            response.onCompleted();
        }

        private static Card card() {
            return Card.newBuilder()
                    .setId("76c0477d-9b0a-45e2-851b-1403298b22ab")
                    .setExternalId("base1-58")
                    .setName("Pikachu")
                    .setSetId("base1")
                    .setSetName("Base Set")
                    .setSetSeries("Base")
                    .setRarity("Common")
                    .setImageUrl("https://images.example/pikachu-small.png")
                    .setImageUrlLarge("https://images.example/pikachu.png")
                    .setReleaseDate("1999-01-09")
                    .build();
        }
    }
}
