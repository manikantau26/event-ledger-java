package com.eventledger.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventRepository repository;
    private final AccountClient accountClient;
    private final ObjectMapper objectMapper;
    private final Counter eventsCreated;
    private final Counter duplicateEvents;

    public EventController(
            EventRepository repository,
            AccountClient accountClient,
            ObjectMapper objectMapper,
            MeterRegistry registry
    ) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.objectMapper = objectMapper;
        this.eventsCreated = registry.counter("gateway.events.created.total");
        this.duplicateEvents = registry.counter("gateway.events.duplicates.total");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "event-gateway",
                "database", "ok"
        );
    }

    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@Valid @RequestBody EventRequest request) throws JsonProcessingException {
        if (!request.type().equals("CREDIT") && !request.type().equals("DEBIT")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "type must be CREDIT or DEBIT"
            ));
        }

        if (repository.existsById(request.eventId())) {
            duplicateEvents.increment();

            EventEntity existing = repository.findById(request.eventId()).orElseThrow();

            log.info("Duplicate event received eventId={}", request.eventId());

            return ResponseEntity.ok(toResponse(existing));
        }

        try {
            accountClient.applyTransaction(request);
        } catch (AccountClient.AccountServiceUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", ex.getMessage()
            ));
        }

        EventEntity entity = new EventEntity();
        entity.setEventId(request.eventId());
        entity.setAccountId(request.accountId());
        entity.setType(request.type());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency().toUpperCase());
        entity.setEventTimestamp(request.eventTimestamp());
        entity.setMetadataJson(objectMapper.writeValueAsString(request.metadata()));
        entity.setTraceId(MDC.get("trace_id"));
        entity.setCreatedAt(Instant.now());

        repository.save(entity);
        eventsCreated.increment();

        log.info("Event accepted eventId={} accountId={}", request.eventId(), request.accountId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEvent(@PathVariable String id) {
        return repository.findById(id)
                .<ResponseEntity<?>>map(event -> ResponseEntity.ok(toResponse(event)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "event not found"
                )));
    }

    @GetMapping("/events")
    public List<Map<String, Object>> getEventsForAccount(@RequestParam String account) {
        return repository.findByAccountIdOrderByEventTimestampAscCreatedAtAsc(account)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId) {
        try {
            return ResponseEntity.ok(accountClient.getBalance(accountId));
        } catch (AccountClient.AccountServiceUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Account Service unreachable; balance unavailable"
            ));
        }
    }

    private Map<String, Object> toResponse(EventEntity event) {
        return Map.of(
                "eventId", event.getEventId(),
                "accountId", event.getAccountId(),
                "type", event.getType(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "eventTimestamp", event.getEventTimestamp(),
                "metadata", event.getMetadataJson(),
                "traceId", event.getTraceId(),
                "createdAt", event.getCreatedAt()
        );
    }
}