package com.vendex.overlap.domain;

public enum CardCondition {
    DMG(0), HP(1), MP(2), LP(3), NM(4);

    private final int quality;

    CardCondition(int quality) {
        this.quality = quality;
    }

    public boolean meets(CardCondition minimum) {
        return quality >= minimum.quality;
    }

    public int qualityMargin(CardCondition minimum) {
        return quality - minimum.quality;
    }

    public static CardCondition parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("card condition is required");
        }
        return valueOf(value.toUpperCase());
    }
}
