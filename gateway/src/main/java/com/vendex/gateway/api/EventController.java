package com.vendex.gateway.api;

import com.vendex.event.v1.Event;
import com.vendex.event.v1.EventRegistration;
import com.vendex.event.v1.EventServiceGrpc;
import com.vendex.event.v1.GetEventRequest;
import com.vendex.event.v1.ListEventsRequest;
import com.vendex.event.v1.ListEventRegistrationsForUserRequest;
import com.vendex.event.v1.RegisterForEventRequest;
import com.vendex.event.v1.RegistrationRole;
import com.vendex.event.v1.UnregisterFromEventRequest;
import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.workflow.EventAccessService;
import com.vendex.gateway.workflow.GrpcRequestSupport;
import com.vendex.gateway.workflow.VendorDirectory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventServiceGrpc.EventServiceBlockingStub events;
    private final GatewayProperties properties;
    private final EventAccessService eventAccess;
    private final VendorDirectory vendors;

    public EventController(
            EventServiceGrpc.EventServiceBlockingStub events,
            GatewayProperties properties,
            EventAccessService eventAccess,
            VendorDirectory vendors) {
        this.events = events;
        this.properties = properties;
        this.eventAccess = eventAccess;
        this.vendors = vendors;
    }

    @GetMapping
    PageResponse<EventResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        vendor(request);
        var response = stub().listEvents(ListEventsRequest.newBuilder()
                .setPageSize(pageSize)
                .setPageOffset(pageOffset)
                .build());
        return new PageResponse<>(response.getEventsList().stream()
                .map(EventController::map)
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @GetMapping("/{eventId}")
    EventResponse get(HttpServletRequest request, @PathVariable String eventId) {
        vendor(request);
        return map(stub().getEvent(GetEventRequest.newBuilder().setEventId(eventId).build()).getEvent());
    }

    @GetMapping("/registrations")
    PageResponse<RegistrationResponse> registrations(
            HttpServletRequest request,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "0") @Min(0) int pageOffset) {
        GatewayPrincipal principal = vendor(request);
        var response = stub().listEventRegistrationsForUser(
                ListEventRegistrationsForUserRequest.newBuilder()
                        .setUserId(principal.userId().toString())
                        .setRole(RegistrationRole.REGISTRATION_ROLE_VENDOR)
                        .setPageSize(pageSize)
                        .setPageOffset(pageOffset)
                        .build());
        return new PageResponse<>(response.getRegistrationsList().stream()
                .map(registration -> map(registration, null))
                .toList(), response.getNextPageOffset(), response.getHasMore());
    }

    @PostMapping("/{eventId}/register")
    ResponseEntity<RegistrationResponse> register(
            HttpServletRequest request,
            @PathVariable String eventId,
            @Valid @RequestBody(required = false) RegistrationBody body) {
        GatewayPrincipal principal = vendor(request);
        String booth = body == null || body.booth() == null ? "" : body.booth();
        var response = stub().registerForEvent(RegisterForEventRequest.newBuilder()
                .setEventId(eventId)
                .setUserId(principal.userId().toString())
                .setRole(RegistrationRole.REGISTRATION_ROLE_VENDOR)
                .setBooth(booth)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(map(response.getRegistration(), null));
    }

    @DeleteMapping("/{eventId}/register")
    ResponseEntity<Void> unregister(HttpServletRequest request, @PathVariable String eventId) {
        GatewayPrincipal principal = vendor(request);
        stub().unregisterFromEvent(UnregisterFromEventRequest.newBuilder()
                .setEventId(eventId)
                .setUserId(principal.userId().toString())
                .build());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/vendors")
    VendorRosterResponse vendorRoster(HttpServletRequest request, @PathVariable String eventId) {
        EventAccessService.VendorRoster roster = eventAccess.requireRegisteredVendor(request, eventId);
        Map<String, VendorDirectory.PublicVendor> directory = vendors.findAll(
                request, roster.registrations().keySet(), roster);
        return new VendorRosterResponse(roster.registrations().values().stream()
                .map(registration -> map(registration, directory.get(registration.getUserId())))
                .toList());
    }

    private GatewayPrincipal vendor(HttpServletRequest request) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        return principal;
    }

    private EventServiceGrpc.EventServiceBlockingStub stub() {
        return GrpcRequestSupport.deadline(events, properties);
    }

    private static EventResponse map(Event event) {
        return new EventResponse(
                event.getId(), event.getName(), event.getCity(), event.getState(), event.getVenue(),
                event.getStartDate(), event.getEndDate(), event.getDescription(),
                event.getCreatedAtEpochSeconds(), event.getUpdatedAtEpochSeconds());
    }

    private static RegistrationResponse map(
            EventRegistration registration,
            VendorDirectory.PublicVendor vendor) {
        return new RegistrationResponse(
                registration.getId(), registration.getEventId(), registration.getUserId(),
                "vendor", registration.getBooth(), registration.getRegisteredAtEpochSeconds(), vendor);
    }

    public record RegistrationBody(@Size(max = 40) String booth) {}

    public record EventResponse(
            String id,
            String name,
            String city,
            String state,
            String venue,
            String startDate,
            String endDate,
            String description,
            long createdAtEpochSeconds,
            long updatedAtEpochSeconds) {}

    public record RegistrationResponse(
            String id,
            String eventId,
            String userId,
            String role,
            String booth,
            long registeredAtEpochSeconds,
            VendorDirectory.PublicVendor vendor) {}

    public record VendorRosterResponse(java.util.List<RegistrationResponse> vendors) {}
}
