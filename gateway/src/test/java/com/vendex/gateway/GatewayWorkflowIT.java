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
import com.vendex.auth.v1.Role;
import com.vendex.auth.v1.VendorProfile;
import com.vendex.buylist.v1.AddWantedCardRequest;
import com.vendex.buylist.v1.AddWantedCardResponse;
import com.vendex.buylist.v1.BuyListServiceGrpc;
import com.vendex.buylist.v1.ListBuyListRequest;
import com.vendex.buylist.v1.ListBuyListsForEventRequest;
import com.vendex.buylist.v1.ListWantedCardsResponse;
import com.vendex.buylist.v1.RemoveWantedCardRequest;
import com.vendex.buylist.v1.RemoveWantedCardResponse;
import com.vendex.buylist.v1.UpdateWantedCardRequest;
import com.vendex.buylist.v1.UpdateWantedCardResponse;
import com.vendex.buylist.v1.WantedCard;
import com.vendex.event.v1.Event;
import com.vendex.event.v1.EventRegistration;
import com.vendex.event.v1.EventServiceGrpc;
import com.vendex.event.v1.GetEventRegistrationsResponse;
import com.vendex.event.v1.GetEventRequest;
import com.vendex.event.v1.GetEventResponse;
import com.vendex.event.v1.GetEventVendorsRequest;
import com.vendex.event.v1.ListEventsRequest;
import com.vendex.event.v1.ListEventsResponse;
import com.vendex.event.v1.RegisterForEventRequest;
import com.vendex.event.v1.RegisterForEventResponse;
import com.vendex.event.v1.RegistrationRole;
import com.vendex.event.v1.UnregisterFromEventRequest;
import com.vendex.event.v1.UnregisterFromEventResponse;
import com.vendex.inventory.v1.AddInventoryRequest;
import com.vendex.inventory.v1.AddInventoryResponse;
import com.vendex.inventory.v1.BulkImportCSVRequest;
import com.vendex.inventory.v1.BulkImportCSVResponse;
import com.vendex.inventory.v1.InventoryItem;
import com.vendex.inventory.v1.InventoryPriority;
import com.vendex.inventory.v1.InventoryServiceGrpc;
import com.vendex.inventory.v1.ListInventoryByEventRequest;
import com.vendex.inventory.v1.ListInventoryRequest;
import com.vendex.inventory.v1.ListInventoryResponse;
import com.vendex.inventory.v1.RemoveInventoryItemRequest;
import com.vendex.inventory.v1.RemoveInventoryItemResponse;
import com.vendex.inventory.v1.SearchInventoryForEventRequest;
import com.vendex.inventory.v1.UpdateInventoryItemRequest;
import com.vendex.inventory.v1.UpdateInventoryItemResponse;
import com.vendex.notification.v1.DigestMode;
import com.vendex.notification.v1.GetNotificationsRequest;
import com.vendex.notification.v1.GetNotificationsResponse;
import com.vendex.notification.v1.GetNotificationPreferencesRequest;
import com.vendex.notification.v1.GetUnreadCountRequest;
import com.vendex.notification.v1.GetUnreadCountResponse;
import com.vendex.notification.v1.InterestStatus;
import com.vendex.notification.v1.ListInterestsForOverlapRequest;
import com.vendex.notification.v1.ListInterestsForOverlapResponse;
import com.vendex.notification.v1.MarkAsReadRequest;
import com.vendex.notification.v1.MarkAsReadResponse;
import com.vendex.notification.v1.Notification;
import com.vendex.notification.v1.NotificationPreferences;
import com.vendex.notification.v1.NotificationPreferencesResponse;
import com.vendex.notification.v1.NotificationServiceGrpc;
import com.vendex.notification.v1.NotificationTrigger;
import com.vendex.notification.v1.OverlapInterest;
import com.vendex.notification.v1.UpdateNotificationPreferencesRequest;
import com.vendex.overlap.v1.GetOverlapsForVendorRequest;
import com.vendex.overlap.v1.ListOverlapsResponse;
import com.vendex.overlap.v1.ListSavedOverlapsRequest;
import com.vendex.overlap.v1.ListSavedOverlapsResponse;
import com.vendex.overlap.v1.Overlap;
import com.vendex.overlap.v1.OverlapServiceGrpc;
import com.vendex.overlap.v1.SaveOverlapRequest;
import com.vendex.overlap.v1.SaveOverlapResponse;
import com.vendex.overlap.v1.SavedOverlap;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayWorkflowIT {

    private static final String USER_ID = "91822be5-4080-42ae-a744-d80c14ed79a4";
    private static final String OTHER_VENDOR_ID = "e246aa51-360f-41bb-bbf2-161c29ce41f7";
    private static final String EVENT_ID = "d010f011-29f3-477b-a065-8201467aa88d";
    private static final String UNREGISTERED_EVENT_ID = "7af827f8-a808-4f1b-820e-e66f12d75762";
    private static final String CARD_ID = "73363f5a-cdf5-435d-ab2d-a687a779b8a8";
    private static final String INVENTORY_ID = "4e0bb810-8567-4772-a4bb-cf1f553d7529";
    private static final String WANTED_ID = "01013127-2598-4fc0-81ce-adc2109da0c0";
    private static final String OVERLAP_ID = "59aa9ee3-f5ed-42e1-ac4d-2da74e242bb1";
    private static final String SAVED_ID = "97c4454d-f02c-4f61-a07f-276b4bd9aa1d";
    private static final String NOTIFICATION_ID = "6ee898d5-7525-442a-96f9-bfa83040efc3";
    private static final String INTEREST_ID = "3523c583-578c-474f-86ee-b42d12b36a5d";
    private static final String KID = "workflow-test-key";
    private static final RSAKey SIGNING_KEY = signingKey();
    private static final Context.Key<String> FORWARDED_AUTHORIZATION = Context.key("workflow-authorization");
    private static final AtomicReference<String> LAST_INVENTORY_VENDOR = new AtomicReference<>();
    private static final AtomicReference<String> LAST_BUYLIST_VENDOR = new AtomicReference<>();
    private static final AtomicReference<String> LAST_REGISTRATION_USER = new AtomicReference<>();
    private static final AtomicReference<String> LAST_PREFERENCES_USER = new AtomicReference<>();
    private static final AtomicInteger INVENTORY_SEARCHES = new AtomicInteger();
    private static final Server GRPC = grpcServer();
    private static final GenericContainer<?> REDIS = redis();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        String target = "localhost:" + GRPC.getPort();
        registry.add("gateway.services.auth-target", () -> target);
        registry.add("gateway.services.card-catalog-target", () -> target);
        registry.add("gateway.services.event-target", () -> target);
        registry.add("gateway.services.inventory-target", () -> target);
        registry.add("gateway.services.buy-list-target", () -> target);
        registry.add("gateway.services.overlap-target", () -> target);
        registry.add("gateway.services.notification-target", () -> target);
        registry.add("gateway.rate-limit.edge-requests", () -> "1000");
        registry.add("gateway.rate-limit.public-requests", () -> "1000");
        registry.add("gateway.rate-limit.authenticated-requests", () -> "1000");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @AfterAll
    static void stopGrpc() {
        GRPC.shutdownNow();
    }

    @Test
    void translatesVendorWorkflowsAndDerivesEveryOwnershipFieldFromTheJwt() throws Exception {
        JsonNode events = json(request("/api/v1/events", HttpMethod.GET, null));
        assertThat(events.get("items").get(0).get("name").asText()).isEqualTo("Boston Card Expo");
        JsonNode event = json(request("/api/v1/events/" + EVENT_ID, HttpMethod.GET, null));
        assertThat(event.get("venue").asText()).isEqualTo("Convention Hall");

        ResponseEntity<String> registration = request(
                "/api/v1/events/" + EVENT_ID + "/register", HttpMethod.POST, Map.of("booth", "A-17"));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(LAST_REGISTRATION_USER).hasValue(USER_ID);

        JsonNode roster = json(request("/api/v1/events/" + EVENT_ID + "/vendors", HttpMethod.GET, null));
        assertThat(roster.get("vendors")).hasSize(2);
        JsonNode publicVendor = null;
        for (JsonNode rosterEntry : roster.get("vendors")) {
            if (OTHER_VENDOR_ID.equals(rosterEntry.get("userId").asText())) {
                publicVendor = rosterEntry.get("vendor");
                break;
            }
        }
        assertThat(publicVendor).isNotNull();
        assertThat(publicVendor.get("shopName").asText()).isEqualTo("Counterparty Cards");
        assertThat(publicVendor.get("booth").asText()).isEqualTo("B-12");
        assertThat(publicVendor.has("email")).isFalse();

        Map<String, Object> inventoryBody = Map.ofEntries(
                Map.entry("vendorId", OTHER_VENDOR_ID),
                Map.entry("cardId", CARD_ID),
                Map.entry("eventId", EVENT_ID),
                Map.entry("condition", "nm"),
                Map.entry("quantity", 2),
                Map.entry("askingPrice", "45.00"),
                Map.entry("priority", "normal"));
        assertThat(request("/api/v1/inventory", HttpMethod.POST, inventoryBody).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(LAST_INVENTORY_VENDOR).hasValue(USER_ID);
        JsonNode ownedInventory = json(request(
                "/api/v1/inventory?eventId=" + EVENT_ID, HttpMethod.GET, null));
        assertThat(ownedInventory.get("items").get(0).get("vendorId").asText()).isEqualTo(USER_ID);
        assertThat(request("/api/v1/inventory/" + INVENTORY_ID, HttpMethod.PUT,
                Map.of("eventId", EVENT_ID, "condition", "lp", "quantity", 1,
                        "askingPrice", "40", "priority", "liquidate")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(LAST_INVENTORY_VENDOR).hasValue(USER_ID);
        assertThat(request("/api/v1/inventory/" + INVENTORY_ID, HttpMethod.DELETE, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode importPreview = json(request("/api/v1/inventory/import", HttpMethod.POST,
                Map.of("eventId", EVENT_ID, "csvContent", "card_name,quantity\nPikachu,1", "dryRun", true)));
        assertThat(importPreview.get("committed").asBoolean()).isFalse();
        assertThat(LAST_INVENTORY_VENDOR).hasValue(USER_ID);

        JsonNode supply = json(request(
                "/api/v1/inventory/event/" + EVENT_ID + "/search?cardId=" + CARD_ID,
                HttpMethod.GET, null));
        assertThat(supply.get("items").get(0).get("vendor").get("shopName").asText())
                .isEqualTo("Counterparty Cards");
        assertThat(supply.get("items").get(0).get("vendor").get("booth").asText())
                .isEqualTo("B-12");

        assertThat(request("/api/v1/buylist", HttpMethod.POST,
                Map.of("vendorId", OTHER_VENDOR_ID, "cardId", CARD_ID,
                        "minimumCondition", "lp", "maxBuyPrice", "50", "quantityWanted", 1))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(LAST_BUYLIST_VENDOR).hasValue(USER_ID);
        JsonNode ownedDemand = json(request("/api/v1/buylist", HttpMethod.GET, null));
        assertThat(ownedDemand.get("items").get(0).get("vendorId").asText()).isEqualTo(USER_ID);
        assertThat(request("/api/v1/buylist/" + WANTED_ID, HttpMethod.PUT,
                Map.of("minimumCondition", "nm", "maxBuyPrice", "52", "quantityWanted", 2))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode demand = json(request(
                "/api/v1/buylist/event/" + EVENT_ID, HttpMethod.GET, null));
        assertThat(demand.get("items").get(0).get("vendor").get("shopName").asText())
                .isEqualTo("Counterparty Cards");
        assertThat(request("/api/v1/buylist/" + WANTED_ID, HttpMethod.DELETE, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode overlaps = json(request(
                "/api/v1/overlaps/event/" + EVENT_ID, HttpMethod.GET, null));
        assertThat(overlaps.get("items").get(0).get("counterparty").get("shopName").asText())
                .isEqualTo("Counterparty Cards");
        JsonNode saved = json(request(
                "/api/v1/overlaps/" + OVERLAP_ID + "/save", HttpMethod.POST, null));
        assertThat(saved.get("vendorId").asText()).isEqualTo(USER_ID);
        JsonNode savedPage = json(request(
                "/api/v1/overlaps/event/" + EVENT_ID + "/saved", HttpMethod.GET, null));
        assertThat(savedPage.get("items").get(0).get("vendorId").asText()).isEqualTo(USER_ID);
        JsonNode interests = json(request(
                "/api/v1/overlaps/" + OVERLAP_ID + "/interests", HttpMethod.GET, null));
        assertThat(interests.get("items").get(0).get("interestedVendor").get("shopName").asText())
                .isEqualTo("Counterparty Cards");

        JsonNode notifications = json(request("/api/v1/notifications", HttpMethod.GET, null));
        assertThat(notifications.get("items").get(0).get("counterparty").get("shopName").asText())
                .isEqualTo("Counterparty Cards");
        JsonNode unread = json(request("/api/v1/notifications/unread-count", HttpMethod.GET, null));
        assertThat(unread.get("unreadCount").asLong()).isEqualTo(1);
        JsonNode read = json(request(
                "/api/v1/notifications/" + NOTIFICATION_ID + "/read", HttpMethod.PATCH, null));
        assertThat(read.get("read").asBoolean()).isTrue();
        JsonNode currentPreferences = json(request(
                "/api/v1/notifications/preferences", HttpMethod.GET, null));
        assertThat(currentPreferences.get("digestMode").asText()).isEqualTo("real_time");
        JsonNode preferences = json(request("/api/v1/notifications/preferences", HttpMethod.PATCH,
                Map.of("emailEnabled", true, "digestMode", "daily",
                        "mutedEventIds", List.of(EVENT_ID))));
        assertThat(preferences.get("digestMode").asText()).isEqualTo("daily");
        assertThat(LAST_PREFERENCES_USER).hasValue(USER_ID);

        assertThat(request(
                "/api/v1/events/" + EVENT_ID + "/register", HttpMethod.DELETE, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(LAST_REGISTRATION_USER).hasValue(USER_ID);
    }

    @Test
    void deniesEventScopedDataBeforeCallingTheDomainServiceWhenVendorIsNotRegistered() throws Exception {
        int searchesBefore = INVENTORY_SEARCHES.get();
        ResponseEntity<String> response = request(
                "/api/v1/inventory/event/" + UNREGISTERED_EVENT_ID + "/search?cardId=" + CARD_ID,
                HttpMethod.GET, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(response).get("code").asText()).isEqualTo("EVENT_REGISTRATION_REQUIRED");
        assertThat(INVENTORY_SEARCHES).hasValue(searchesBefore);
    }

    private ResponseEntity<String> request(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-ID", "workflow-it");
        return rest.exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
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
                            call, headers, next);
                }
            };
            return ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(new FakeAuthService(), captureAuthorization))
                    .addService(new FakeEventService())
                    .addService(new FakeInventoryService())
                    .addService(new FakeBuyListService())
                    .addService(new FakeOverlapService())
                    .addService(new FakeNotificationService())
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
                            .subject(USER_ID)
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

    private static VendorProfile profile(String userId) {
        boolean caller = USER_ID.equals(userId);
        return VendorProfile.newBuilder()
                .setUserId(userId)
                .setEmail(caller ? "caller@example.com" : "must-not-leak@example.com")
                .setRole(Role.ROLE_VENDOR)
                .setShopName(caller ? "Caller Cards" : "Counterparty Cards")
                .setCity("Boston")
                .setState("MA")
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .build();
    }

    private static Event event() {
        return Event.newBuilder()
                .setId(EVENT_ID)
                .setOrganizerId("86077f25-0294-4ecb-b204-0abbb19d092f")
                .setName("Boston Card Expo")
                .setCity("Boston")
                .setState("MA")
                .setVenue("Convention Hall")
                .setStartDate("2026-09-12")
                .setEndDate("2026-09-13")
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .setUpdatedAtEpochSeconds(1_700_000_000L)
                .build();
    }

    private static EventRegistration registration(String eventId, String userId, String booth) {
        return EventRegistration.newBuilder()
                .setId(UUID.nameUUIDFromBytes((eventId + userId).getBytes()).toString())
                .setEventId(eventId)
                .setUserId(userId)
                .setRole(RegistrationRole.REGISTRATION_ROLE_VENDOR)
                .setBooth(booth)
                .setRegisteredAtEpochSeconds(1_700_000_000L)
                .build();
    }

    private static InventoryItem inventoryItem(String vendorId) {
        return InventoryItem.newBuilder()
                .setId(INVENTORY_ID)
                .setVendorId(vendorId)
                .setCardId(CARD_ID)
                .setEventId(EVENT_ID)
                .setCondition(com.vendex.inventory.v1.CardCondition.CARD_CONDITION_NM)
                .setQuantity(2)
                .setAskingPrice("45")
                .setPriority(InventoryPriority.INVENTORY_PRIORITY_NORMAL)
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .setUpdatedAtEpochSeconds(1_700_000_001L)
                .build();
    }

    private static WantedCard wantedCard(String vendorId) {
        return WantedCard.newBuilder()
                .setId(WANTED_ID)
                .setVendorId(vendorId)
                .setCardId(CARD_ID)
                .setMinimumCondition(com.vendex.buylist.v1.CardCondition.CARD_CONDITION_LP)
                .setMaxBuyPrice("50")
                .setQuantityWanted(1)
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .setUpdatedAtEpochSeconds(1_700_000_001L)
                .build();
    }

    private static Overlap overlap() {
        return Overlap.newBuilder()
                .setId(OVERLAP_ID)
                .setEventId(EVENT_ID)
                .setBuyerVendorId(USER_ID)
                .setSellerVendorId(OTHER_VENDOR_ID)
                .setCardId(CARD_ID)
                .setInventoryItemId(INVENTORY_ID)
                .setWantedCardId(WANTED_ID)
                .setSellerCondition("NM")
                .setMinimumCondition("LP")
                .setAvailableQuantity(2)
                .setQuantityWanted(1)
                .setAskingPrice("45")
                .setMaxBuyPrice("50")
                .setInventoryPriority("normal")
                .setScore("0.91")
                .setActive(true)
                .setCreatedAtEpochSeconds(1_700_000_000L)
                .setUpdatedAtEpochSeconds(1_700_000_001L)
                .build();
    }

    private static final class FakeAuthService extends AuthServiceGrpc.AuthServiceImplBase {
        @Override
        public void getJWKS(GetJWKSRequest request, StreamObserver<GetJWKSResponse> response) {
            response.onNext(GetJWKSResponse.newBuilder().addKeys(Jwk.newBuilder()
                    .setKid(KID).setAlg("RS256").setKty("RSA").setUse("sig")
                    .setN(SIGNING_KEY.toPublicJWK().getModulus().toString())
                    .setE(SIGNING_KEY.toPublicJWK().getPublicExponent().toString())
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
            response.onNext(GetVendorProfileResponse.newBuilder()
                    .setProfile(profile(request.getUserId())).build());
            response.onCompleted();
        }
    }

    private static final class FakeEventService extends EventServiceGrpc.EventServiceImplBase {
        @Override
        public void listEvents(ListEventsRequest request, StreamObserver<ListEventsResponse> response) {
            response.onNext(ListEventsResponse.newBuilder().addEvents(event()).build());
            response.onCompleted();
        }

        @Override
        public void getEvent(GetEventRequest request, StreamObserver<GetEventResponse> response) {
            response.onNext(GetEventResponse.newBuilder().setEvent(event()).build());
            response.onCompleted();
        }

        @Override
        public void registerForEvent(
                RegisterForEventRequest request,
                StreamObserver<RegisterForEventResponse> response) {
            LAST_REGISTRATION_USER.set(request.getUserId());
            response.onNext(RegisterForEventResponse.newBuilder()
                    .setRegistration(registration(request.getEventId(), request.getUserId(), request.getBooth()))
                    .build());
            response.onCompleted();
        }

        @Override
        public void unregisterFromEvent(
                UnregisterFromEventRequest request,
                StreamObserver<UnregisterFromEventResponse> response) {
            LAST_REGISTRATION_USER.set(request.getUserId());
            response.onNext(UnregisterFromEventResponse.getDefaultInstance());
            response.onCompleted();
        }

        @Override
        public void getEventVendors(
                GetEventVendorsRequest request,
                StreamObserver<GetEventRegistrationsResponse> response) {
            GetEventRegistrationsResponse.Builder roster = GetEventRegistrationsResponse.newBuilder();
            if (!UNREGISTERED_EVENT_ID.equals(request.getEventId())) {
                roster.addRegistrations(registration(request.getEventId(), USER_ID, "A-17"));
            }
            roster.addRegistrations(registration(request.getEventId(), OTHER_VENDOR_ID, "B-12"));
            response.onNext(roster.build());
            response.onCompleted();
        }
    }

    private static final class FakeInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {
        @Override
        public void addInventory(AddInventoryRequest request, StreamObserver<AddInventoryResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(AddInventoryResponse.newBuilder()
                    .setItem(inventoryItem(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void updateItem(
                UpdateInventoryItemRequest request,
                StreamObserver<UpdateInventoryItemResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(UpdateInventoryItemResponse.newBuilder()
                    .setItem(inventoryItem(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void removeItem(
                RemoveInventoryItemRequest request,
                StreamObserver<RemoveInventoryItemResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(RemoveInventoryItemResponse.newBuilder()
                    .setRemovedItem(inventoryItem(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void bulkImportCSV(
                BulkImportCSVRequest request,
                StreamObserver<BulkImportCSVResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(BulkImportCSVResponse.newBuilder().setCommitted(!request.getDryRun()).build());
            response.onCompleted();
        }

        @Override
        public void listInventory(
                ListInventoryRequest request,
                StreamObserver<ListInventoryResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(ListInventoryResponse.newBuilder()
                    .addItems(inventoryItem(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void listInventoryByEvent(
                ListInventoryByEventRequest request,
                StreamObserver<ListInventoryResponse> response) {
            LAST_INVENTORY_VENDOR.set(request.getVendorId());
            response.onNext(ListInventoryResponse.newBuilder()
                    .addItems(inventoryItem(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void searchInventoryForEvent(
                SearchInventoryForEventRequest request,
                StreamObserver<ListInventoryResponse> response) {
            INVENTORY_SEARCHES.incrementAndGet();
            response.onNext(ListInventoryResponse.newBuilder()
                    .addItems(inventoryItem(OTHER_VENDOR_ID)).build());
            response.onCompleted();
        }
    }

    private static final class FakeBuyListService extends BuyListServiceGrpc.BuyListServiceImplBase {
        @Override
        public void addWantedCard(
                AddWantedCardRequest request,
                StreamObserver<AddWantedCardResponse> response) {
            LAST_BUYLIST_VENDOR.set(request.getVendorId());
            response.onNext(AddWantedCardResponse.newBuilder()
                    .setWantedCard(wantedCard(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void updateWantedCard(
                UpdateWantedCardRequest request,
                StreamObserver<UpdateWantedCardResponse> response) {
            LAST_BUYLIST_VENDOR.set(request.getVendorId());
            response.onNext(UpdateWantedCardResponse.newBuilder()
                    .setWantedCard(wantedCard(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void removeWantedCard(
                RemoveWantedCardRequest request,
                StreamObserver<RemoveWantedCardResponse> response) {
            LAST_BUYLIST_VENDOR.set(request.getVendorId());
            response.onNext(RemoveWantedCardResponse.newBuilder()
                    .setRemovedWantedCard(wantedCard(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void listBuyList(
                ListBuyListRequest request,
                StreamObserver<ListWantedCardsResponse> response) {
            LAST_BUYLIST_VENDOR.set(request.getVendorId());
            response.onNext(ListWantedCardsResponse.newBuilder()
                    .addWantedCards(wantedCard(request.getVendorId())).build());
            response.onCompleted();
        }

        @Override
        public void listBuyListsForEvent(
                ListBuyListsForEventRequest request,
                StreamObserver<ListWantedCardsResponse> response) {
            response.onNext(ListWantedCardsResponse.newBuilder()
                    .addWantedCards(wantedCard(OTHER_VENDOR_ID)).build());
            response.onCompleted();
        }
    }

    private static final class FakeOverlapService extends OverlapServiceGrpc.OverlapServiceImplBase {
        @Override
        public void getOverlapsForVendor(
                GetOverlapsForVendorRequest request,
                StreamObserver<ListOverlapsResponse> response) {
            response.onNext(ListOverlapsResponse.newBuilder().addOverlaps(overlap()).build());
            response.onCompleted();
        }

        @Override
        public void saveOverlap(
                SaveOverlapRequest request,
                StreamObserver<SaveOverlapResponse> response) {
            response.onNext(SaveOverlapResponse.newBuilder().setSavedOverlap(SavedOverlap.newBuilder()
                    .setId(SAVED_ID).setVendorId(request.getVendorId()).setOverlap(overlap())
                    .setCreatedAtEpochSeconds(1_700_000_002L).build()).build());
            response.onCompleted();
        }

        @Override
        public void listSavedOverlaps(
                ListSavedOverlapsRequest request,
                StreamObserver<ListSavedOverlapsResponse> response) {
            response.onNext(ListSavedOverlapsResponse.newBuilder().addSavedOverlaps(
                    SavedOverlap.newBuilder().setId(SAVED_ID).setVendorId(request.getVendorId())
                            .setOverlap(overlap()).setCreatedAtEpochSeconds(1_700_000_002L).build())
                    .build());
            response.onCompleted();
        }
    }

    private static final class FakeNotificationService
            extends NotificationServiceGrpc.NotificationServiceImplBase {
        @Override
        public void getNotifications(
                GetNotificationsRequest request,
                StreamObserver<GetNotificationsResponse> response) {
            response.onNext(GetNotificationsResponse.newBuilder().addNotifications(Notification.newBuilder()
                    .setId(NOTIFICATION_ID).setVendorId(request.getVendorId()).setEventId(EVENT_ID)
                    .setTrigger(NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_BUYLIST)
                    .setOverlapId(OVERLAP_ID).setCardId(CARD_ID)
                    .setCounterpartyVendorId(OTHER_VENDOR_ID).setPayloadJson("{}")
                    .setActive(true).setAvailableAtEpochSeconds(1_700_000_000L)
                    .setCreatedAtEpochSeconds(1_700_000_000L)
                    .setUpdatedAtEpochSeconds(1_700_000_000L).build()).build());
            response.onCompleted();
        }

        @Override
        public void getUnreadCount(
                GetUnreadCountRequest request,
                StreamObserver<GetUnreadCountResponse> response) {
            response.onNext(GetUnreadCountResponse.newBuilder().setUnreadCount(1).build());
            response.onCompleted();
        }

        @Override
        public void markAsRead(
                MarkAsReadRequest request,
                StreamObserver<MarkAsReadResponse> response) {
            response.onNext(MarkAsReadResponse.newBuilder().setNotification(Notification.newBuilder()
                    .setId(request.getNotificationId()).setVendorId(request.getVendorId())
                    .setEventId(EVENT_ID)
                    .setTrigger(NotificationTrigger.NOTIFICATION_TRIGGER_OVERLAP_BUYLIST)
                    .setOverlapId(OVERLAP_ID).setCardId(CARD_ID)
                    .setCounterpartyVendorId(OTHER_VENDOR_ID).setPayloadJson("{}")
                    .setRead(true).setActive(true).setAvailableAtEpochSeconds(1_700_000_000L)
                    .setCreatedAtEpochSeconds(1_700_000_000L)
                    .setUpdatedAtEpochSeconds(1_700_000_001L).build()).build());
            response.onCompleted();
        }

        @Override
        public void getNotificationPreferences(
                GetNotificationPreferencesRequest request,
                StreamObserver<NotificationPreferencesResponse> response) {
            response.onNext(preferences(request.getUserId(), DigestMode.DIGEST_MODE_REAL_TIME));
            response.onCompleted();
        }

        @Override
        public void updateNotificationPreferences(
                UpdateNotificationPreferencesRequest request,
                StreamObserver<NotificationPreferencesResponse> response) {
            LAST_PREFERENCES_USER.set(request.getUserId());
            response.onNext(preferences(request.getUserId(), request.getDigestMode()));
            response.onCompleted();
        }

        @Override
        public void listInterestsForOverlap(
                ListInterestsForOverlapRequest request,
                StreamObserver<ListInterestsForOverlapResponse> response) {
            response.onNext(ListInterestsForOverlapResponse.newBuilder().addInterests(
                    OverlapInterest.newBuilder().setId(INTEREST_ID).setOverlapId(OVERLAP_ID)
                            .setInterestedVendorId(OTHER_VENDOR_ID)
                            .setCounterpartyVendorId(request.getSellerVendorId())
                            .setEventId(EVENT_ID).setScore("0.91")
                            .setStatus(InterestStatus.INTEREST_STATUS_PENDING)
                            .setCreatedAtEpochSeconds(1_700_000_000L)
                            .setUpdatedAtEpochSeconds(1_700_000_001L).build()).build());
            response.onCompleted();
        }

        private static NotificationPreferencesResponse preferences(String userId, DigestMode mode) {
            return NotificationPreferencesResponse.newBuilder().setPreferences(
                    NotificationPreferences.newBuilder().setUserId(userId)
                            .setInAppEnabled(true).setEmailEnabled(true)
                            .setOverlapBuylistEnabled(true).setOverlapLiquidateEnabled(true)
                            .setSavedOverlapActiveEnabled(true).setDigestMode(mode)
                            .addMutedEventIds(EVENT_ID).setUpdatedAtEpochSeconds(1_700_000_000L)
                            .build()).build();
        }
    }
}
