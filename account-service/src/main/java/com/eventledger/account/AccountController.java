package com.eventledger.account;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final TransactionRepository repository;
    private final Counter transactionsApplied;

    public AccountController(TransactionRepository repository, MeterRegistry registry) {
        this.repository = repository;
        this.transactionsApplied = registry.counter("account.transactions.applied.total");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "account-service",
                "database", "ok"
        );
    }

    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<Map<String, Object>> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request
    ) {
        if (!request.type().equals("CREDIT") && !request.type().equals("DEBIT")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "type must be CREDIT or DEBIT"
            ));
        }

        if (repository.existsById(request.eventId())) {
            log.info("Duplicate transaction ignored eventId={}", request.eventId());

            return ResponseEntity.ok(Map.of(
                    "status", "duplicate",
                    "eventId", request.eventId()
            ));
        }

        TransactionEntity entity = new TransactionEntity();
        entity.setEventId(request.eventId());
        entity.setAccountId(accountId);
        entity.setType(request.type());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setEventTimestamp(request.eventTimestamp());
        entity.setTraceId(MDC.get("trace_id"));

        repository.save(entity);
        transactionsApplied.increment();

        log.info("Transaction applied eventId={} accountId={}", request.eventId(), accountId);

        return ResponseEntity.ok(Map.of(
                "status", "applied",
                "eventId", request.eventId()
        ));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public Map<String, Object> getBalance(@PathVariable String accountId) {
        List<TransactionEntity> transactions = repository.findByAccountIdOrderByEventTimestampDesc(accountId);

        BigDecimal balance = BigDecimal.ZERO;
        String currency = "USD";

        for (TransactionEntity tx : transactions) {
            currency = tx.getCurrency();

            if (tx.getType().equals("CREDIT")) {
                balance = balance.add(tx.getAmount());
            } else {
                balance = balance.subtract(tx.getAmount());
            }
        }

        log.info("Balance queried accountId={}", accountId);

        return Map.of(
                "accountId", accountId,
                "balance", balance,
                "currency", currency
        );
    }

    @GetMapping("/accounts/{accountId}")
    public Map<String, Object> getAccount(@PathVariable String accountId) {
        List<TransactionEntity> transactions = repository.findByAccountIdOrderByEventTimestampDesc(accountId);

        List<TransactionEntity> recent = transactions.stream()
                .sorted(Comparator.comparing(TransactionEntity::getEventTimestamp).reversed())
                .limit(10)
                .toList();

        return Map.of(
                "accountId", accountId,
                "balance", getBalance(accountId),
                "recentTransactions", recent
        );
    }
}