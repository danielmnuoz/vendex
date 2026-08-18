package com.vendex.overlap.domain;

import java.time.Instant;
import java.util.UUID;

public record SavedOverlap(
        UUID id,
        UUID vendorId,
        Overlap overlap,
        Instant createdAt
) {}
