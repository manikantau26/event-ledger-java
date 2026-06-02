# Event Ledger

This project implements a simple event ledger system using two Spring Boot microservices:

- Event Gateway API
- Account Service

The system processes financial transaction events and is designed to handle duplicate event submissions, out-of-order event delivery, service failures, and basic observability requirements.

## Architecture

The Event Gateway is the entry point for client requests. It validates incoming events, enforces idempotency, stores event records, and forwards transactions to the Account Service.

The Account Service owns account state and is responsible for storing transactions and calculating balances.

The services communicate through synchronous REST calls and maintain separate databases to keep service boundaries clear.

```text
Client
   |
   v
+-------------------+
| Event Gateway API |
|      :8080        |
+---------+---------+
          |
          v
+-------------------+
| Account Service   |
|      :8081        |
+-------------------+
```

## Features

### Idempotent Event Processing

The Gateway stores events using the provided `eventId`.

If the same event is submitted multiple times:

- The original event is returned
- The account balance is not updated again
- Duplicate processing is avoided

### Out-of-Order Event Handling

Events may arrive in any order.

When retrieving events for an account, results are sorted using the original `eventTimestamp` rather than arrival time.

### Balance Calculation

Account balances are calculated as:

```text
Total Credits - Total Debits
```

Example:

```text
CREDIT 150
CREDIT 50
DEBIT 25

Balance = 175
```

### Distributed Tracing

Each request is associated with a trace ID.

The Gateway:

- Accepts an incoming `X-Trace-Id`
- Generates one if none is provided
- Forwards the same trace ID to Account Service

Both services include the trace ID in structured JSON logs so a request can be followed across service boundaries.

### Observability

Both services expose:

- Health endpoints
- Structured JSON logging
- Application metrics through Spring Actuator
- Prometheus-compatible metrics endpoint
- Trace-aware logging using a shared trace ID

Prometheus metrics are available through:

```text
/actuator/prometheus
```

### Resiliency

Communication between the Gateway and Account Service includes:

- Request timeouts
- Retry with exponential backoff and jitter
- Circuit breaker protection

Exponential backoff helps reduce pressure on a failing downstream service, while jitter helps prevent synchronized retry storms.

If Account Service becomes unavailable:

- `POST /events` returns `503 Service Unavailable`
- Event lookup endpoints continue to work
- Balance requests return a clear error response

## Technology Stack

- Java 17
- Spring Boot 3
- Spring Data JPA
- H2 Database
- Spring Actuator
- Micrometer
- Prometheus Registry
- Maven
- Docker Compose
- JUnit 5

## API Endpoints

### Event Gateway API (Port 8080)

| Method | Endpoint | Description |
|----------|----------|----------|
| POST | /events | Submit transaction event |
| GET | /events/{id} | Get event by ID |
| GET | /events?account={accountId} | List account events |
| GET | /accounts/{accountId}/balance | Get balance |
| GET | /health | Health check |
| GET | /actuator/metrics | Metrics |
| GET | /actuator/prometheus | Prometheus metrics |

### Account Service (Port 8081)

| Method | Endpoint | Description |
|----------|----------|----------|
| POST | /accounts/{accountId}/transactions | Apply transaction |
| GET | /accounts/{accountId}/balance | Get balance |
| GET | /accounts/{accountId} | Account details |
| GET | /health | Health check |
| GET | /actuator/metrics | Metrics |
| GET | /actuator/prometheus | Prometheus metrics |

## Running Locally

### Prerequisites

- Java 17+
- Maven 3.9+
- Git

Verify installation:

```bash
java -version
mvn -version
git --version
```

### Start Account Service

Open Terminal 1:

```bash
cd account-service
mvn spring-boot:run
```

Verify:

```bash
curl http://localhost:8081/health
```

### Start Event Gateway

Open Terminal 2:

```bash
cd event-gateway
mvn spring-boot:run
```

Verify:

```bash
curl http://localhost:8080/health
```

## Example Request

Create an event:

```bash
curl -X POST http://localhost:8080/events \
-H "Content-Type: application/json" \
-H "X-Trace-Id: demo-trace-1" \
-d '{
  "eventId":"evt-001",
  "accountId":"acct-123",
  "type":"CREDIT",
  "amount":150.00,
  "currency":"USD",
  "eventTimestamp":"2026-05-15T14:02:11Z",
  "metadata":{
    "source":"mainframe-batch",
    "batchId":"B-9042"
  }
}'
```

Expected:

```text
HTTP/1.1 201 Created
```

## Testing Idempotency

Submit the same request again.

Expected:

```text
HTTP/1.1 200 OK
```

The existing event is returned and the balance remains unchanged.

## Testing Out-of-Order Events

Submit events with timestamps that are not in arrival order and then query:

```bash
curl "http://localhost:8080/events?account=acct-123"
```

Events will be returned ordered by:

```text
eventTimestamp ASC
```

regardless of arrival order.

## Metrics

Application metrics are available through Spring Actuator.

Examples:

```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus

curl http://localhost:8081/actuator/metrics
curl http://localhost:8081/actuator/prometheus
```

These endpoints can be scraped by Prometheus and visualized through tools such as Grafana.

## Running Tests

From the project root:

```bash
mvn test
```

Expected:

```text
BUILD SUCCESS
```

The test suite covers:

- Validation
- Idempotency
- Out-of-order handling
- Balance calculation
- Trace propagation
- Service degradation
- Gateway → Account Service integration flow

## Running with Docker Compose

Start both services:

```bash
docker compose up --build
```

Verify:

```bash
curl http://localhost:8080/health
curl http://localhost:8081/health
```

Stop:

```bash
docker compose down
```

## Design Notes

A few implementation decisions worth calling out:

- Each service has its own database to maintain clear ownership boundaries.
- Idempotency is enforced in the Gateway and validated again in Account Service as a safety net.
- The Gateway stores events locally, allowing event queries to continue working even if Account Service is unavailable.
- Retry, timeout, and circuit breaker behavior were added to make service-to-service communication more resilient.
- Prometheus metrics were added to improve operational visibility.

## Future Improvements

If this project were extended further, a few areas worth exploring would be:

- OpenTelemetry with Zipkin or Jaeger
- Grafana dashboards
- API rate limiting
- Contract testing with Pact
- Asynchronous recovery queue for failed events
- PostgreSQL instead of in-memory databases