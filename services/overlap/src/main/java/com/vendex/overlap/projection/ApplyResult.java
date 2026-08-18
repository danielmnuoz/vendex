package com.vendex.overlap.projection;

public enum ApplyResult {
    APPLIED,
    DUPLICATE,
    STALE;

    public boolean shouldRecompute() {
        return this != STALE;
    }
}
