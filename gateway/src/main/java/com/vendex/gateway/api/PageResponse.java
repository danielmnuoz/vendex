package com.vendex.gateway.api;

import java.util.List;

public record PageResponse<T>(List<T> items, int nextPageOffset, boolean hasMore) {}
