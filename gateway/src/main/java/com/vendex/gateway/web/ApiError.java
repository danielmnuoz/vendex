package com.vendex.gateway.web;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String requestId,
        String path) {}
