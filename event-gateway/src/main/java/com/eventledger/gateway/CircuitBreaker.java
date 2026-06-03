package com.eventledger.gateway;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class CircuitBreaker {

    /**
     * Number of consecutive failures recorded.
     */
    private int failures = 0;

    /**
     * Instant when the circuit opened after exceeding the failure threshold.
     */
    private Instant openedAt;

    private final int threshold = 3;
    private final Duration resetAfter = Duration.ofSeconds(10);

    public synchronized boolean allowRequest() {
        if (openedAt == null) {
            return true;
        }

        if (Instant.now().isAfter(openedAt.plus(resetAfter))) {
            // Reset the circuit after the configured cooldown period.
            failures = 0;
            openedAt = null;
            return true;
        }

        return false;
    }

    public synchronized void recordSuccess() {
        failures = 0;
        openedAt = null;
    }

    public synchronized void recordFailure() {
        failures++;

        if (failures >= threshold) {
            openedAt = Instant.now();
        }
    }

    public synchronized boolean isOpen() {
        return openedAt != null && Instant.now().isBefore(openedAt.plus(resetAfter));
    }

    public synchronized int getFailures() {
        return failures;
    }
}