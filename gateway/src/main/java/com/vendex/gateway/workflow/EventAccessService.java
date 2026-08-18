package com.vendex.gateway.workflow;

import com.vendex.event.v1.EventRegistration;
import com.vendex.event.v1.EventServiceGrpc;
import com.vendex.event.v1.GetEventVendorsRequest;
import com.vendex.gateway.auth.GatewayPrincipal;
import com.vendex.gateway.auth.GatewayRole;
import com.vendex.gateway.config.GatewayProperties;
import com.vendex.gateway.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EventAccessService {

    private final EventServiceGrpc.EventServiceBlockingStub events;
    private final GatewayProperties properties;

    public EventAccessService(
            EventServiceGrpc.EventServiceBlockingStub events,
            GatewayProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    public VendorRoster requireRegisteredVendor(HttpServletRequest request, String eventId) {
        GatewayPrincipal principal = GatewayPrincipal.require(request);
        principal.requireRole(GatewayRole.VENDOR);
        VendorRoster roster = vendorRoster(eventId);
        if (!roster.registrations().containsKey(principal.userId().toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_REGISTRATION_REQUIRED",
                    "This route requires an active vendor registration for the event");
        }
        return roster;
    }

    public VendorRoster vendorRoster(String eventId) {
        var response = GrpcRequestSupport.deadline(events, properties)
                .getEventVendors(GetEventVendorsRequest.newBuilder().setEventId(eventId).build());
        Map<String, EventRegistration> byVendor = new LinkedHashMap<>();
        response.getRegistrationsList().forEach(registration ->
                byVendor.put(registration.getUserId(), registration));
        return new VendorRoster(Map.copyOf(byVendor));
    }

    public record VendorRoster(Map<String, EventRegistration> registrations) {
        public String booth(String vendorId) {
            EventRegistration registration = registrations.get(vendorId);
            return registration == null ? "" : registration.getBooth();
        }
    }
}
