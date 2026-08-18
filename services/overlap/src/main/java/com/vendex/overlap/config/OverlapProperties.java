package com.vendex.overlap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("overlap")
public class OverlapProperties {
    private BigDecimal scoreThreshold = BigDecimal.ZERO;

    public BigDecimal getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(BigDecimal scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }
}
