package com.eventledger.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.Map;

/**
 * Client for communicating with the account-service.
 *
 * Handles trace propagation, retry backoff, and circuit breaker protection.
 */
@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final CircuitBreaker circuitBreaker;

    public AccountClient(
            RestTemplateBuilder builder,
            @Value("${account-service.base-url}") String baseUrl,
            CircuitBreaker circuitBreaker
    ) {
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(2))
            .setReadTimeout(Duration.ofSeconds(2))
            .build();

        this.baseUrl = baseUrl;
        this.circuitBreaker = circuitBreaker;
    }

    public void applyTransaction(EventRequest event) {
        if (!circuitBreaker.allowRequest()) {
            throw new AccountServiceUnavailableException("Account Service circuit is open");
        }

        String traceId = MDC.get("trace_id");

        Map<String, Object> body = Map.of(
                "eventId", event.eventId(),
                "type", event.type(),
                "amount", event.amount(),
                "currency", event.currency(),
                "eventTimestamp", event.eventTimestamp().toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Trace-Id", traceId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = baseUrl + "/accounts/" + event.accountId() + "/transactions";

        int attempts = 0;

        // Retry failed account-service calls with exponential backoff and jitter.
        while (attempts < 3) {
            attempts++;

            try {
                restTemplate.postForEntity(url, entity, Map.class);
                circuitBreaker.recordSuccess();
                return;
            } catch (RestClientException ex) {
                circuitBreaker.recordFailure();
                log.warn("Account Service call failed attempt={} traceId={}, retrying with backoff", attempts, traceId);

                try {
                    long baseDelayMs = 100L;
                    long exponentialDelayMs = (long) (baseDelayMs * Math.pow(2, attempts - 1));
                    long jitterMs = ThreadLocalRandom.current().nextLong(0, 100);
                    long totalDelayMs = exponentialDelayMs + jitterMs;

                    Thread.sleep(totalDelayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        throw new AccountServiceUnavailableException("Account Service unavailable");
    }

    public Map getBalance(String accountId) {
        if (!circuitBreaker.allowRequest()) {
            throw new AccountServiceUnavailableException("Account Service circuit is open");
        }

        String traceId = MDC.get("trace_id");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", traceId);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/accounts/" + accountId + "/balance",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            circuitBreaker.recordSuccess();
            return response.getBody();

        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            throw new AccountServiceUnavailableException("Account Service unreachable");
        }
    }

    public static class AccountServiceUnavailableException extends RuntimeException {
        public AccountServiceUnavailableException(String message) {
            super(message);
        }
    }
}