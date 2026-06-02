# Event Ledger

Event Ledger is a Java Spring Boot take-home project with two independent microservices.

## Architecture

The system has two services:

1. Event Gateway API
2. Account Service

The Event Gateway receives client requests, validates transaction events, stores events locally, handles idempotency, and calls the Account Service.

The Account Service stores account transactions and calculates balances.

The services communicate through synchronous REST calls and do not share a database.

## Event Gateway API

Runs on port 8080.

Endpoints:

- POST /events
- GET /events/{id}
- GET /events?account={accountId}
- GET /accounts/{accountId}/balance
- GET /health
- GET /actuator/metrics

## Account Service

Runs on port 8081.

Endpoints:

- POST /accounts/{accountId}/transactions
- GET /accounts/{accountId}/balance
- GET /accounts/{accountId}
- GET /health
- GET /actuator/metrics

## Idempotency

The Gateway stores events by eventId.

If the same eventId is submitted more than once, the Gateway returns the original event with HTTP 200 and does not call the Account Service again.

This prevents duplicate balance updates.

The Account Service also checks duplicate eventId values as a second layer of protection.

## Out-of-order handling

Events can arrive in any order.

The Gateway stores the original eventTimestamp and returns event lists ordered by eventTimestamp.

Balances are correct because the Account Service calculates the net balance from all transactions.

## Balance calculation

Balance equals:

CREDIT total minus DEBIT total.

Example:

CREDIT 150 + CREDIT 50 - DEBIT 25 = 175

## Trace propagation

The Gateway accepts X-Trace-Id from the client.

If the client does not send X-Trace-Id, the Gateway generates one.

The Gateway sends the same trace ID to the Account Service.

Both services store the trace ID in MDC so logs can include it.

## Observability

Both services include:

- health endpoint
- actuator metrics
- custom counters
- trace ID propagation
- structured logging support

## Resiliency

The Gateway uses timeout, retry with backoff, and a simple circuit breaker when calling Account Service.

If the Account Service is down, POST /events returns 503 instead of hanging.

Gateway read endpoints still work because they only use Gateway local data.

## Run locally

Start Account Service:

```bash
cd account-service
mvn spring-boot:run