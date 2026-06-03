package com.eventledger.gateway;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Incoming request payload for an event submitted to the event gateway.
 */
public record EventRequest(
        @NotBlank String eventId,
        @NotBlank String accountId,
        @NotBlank String type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotNull Instant eventTimestamp,
        Map<String, Object> metadata
) {
}