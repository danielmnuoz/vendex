package com.vendex.gateway.workflow;

import com.vendex.auth.v1.AuthServiceGrpc;
import com.vendex.auth.v1.GetVendorProfileRequest;
import com.vendex.auth.v1.Role;
import com.vendex.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class VendorDirectory {

    private final AuthServiceGrpc.AuthServiceBlockingStub auth;
    private final GatewayProperties properties;

    public VendorDirectory(
            AuthServiceGrpc.AuthServiceBlockingStub auth,
            GatewayProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    public Map<String, PublicVendor> findAll(
            HttpServletRequest request,
            Iterable<String> vendorIds,
            EventAccessService.VendorRoster roster) {
        Set<String> distinct = new LinkedHashSet<>();
        vendorIds.forEach(vendorId -> {
            if (vendorId != null && !vendorId.isBlank()) {
                distinct.add(vendorId);
            }
        });
        Map<String, PublicVendor> vendors = new LinkedHashMap<>();
        for (String vendorId : distinct) {
            // A deadline is created per lookup; reusing one deadline-bearing stub
            // would make the whole decorated page share a single countdown.
            var profile = GrpcRequestSupport.authenticated(auth, properties, request)
                    .getVendorProfile(GetVendorProfileRequest.newBuilder()
                            .setUserId(vendorId)
                            .build()).getProfile();
            vendors.put(vendorId, new PublicVendor(
                    profile.getUserId(),
                    profile.getShopName(),
                    profile.getCity(),
                    profile.getState(),
                    role(profile.getRole()),
                    roster == null ? "" : roster.booth(vendorId)));
        }
        return Map.copyOf(vendors);
    }

    private static String role(Role role) {
        return switch (role) {
            case ROLE_VENDOR -> "vendor";
            case ROLE_ATTENDEE -> "attendee";
            case ROLE_ORGANIZER -> "organizer";
            default -> "unspecified";
        };
    }

    public record PublicVendor(
            String userId,
            String shopName,
            String city,
            String state,
            String role,
            String booth) {}
}
