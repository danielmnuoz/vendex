package com.vendex.events.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the outbox relay, bound from {@code vendex.outbox.*}.
 */
@ConfigurationProperties("vendex.outbox")
public class OutboxProperties {

    /** Master switch; when false no relay or writer beans are created. */
    private boolean enabled = true;

    /** Max rows drained per poll. Keeps a single tick bounded under backlog. */
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
