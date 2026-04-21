# SwiftPay — Real-Time P2P Payment Ledger

A production-grade, event-driven microservices platform for peer-to-peer money transfers built with Java 21, Spring Boot 3, Kafka, Redis, and PostgreSQL.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Client                                                                 │
│    │  POST /v1/payments  (Idempotency-Key header)                       │
│    ▼                                                                    │
│ ┌──────────────────────────┐                                            │
│ │  Service A               │  ① Idempotency check (Redis 24h TTL)      │
│ │  Transaction Gateway     │  ② Balance check (Redis cache → REST)     │
│ │  :8080                   │  ③ Persist PENDING to PostgreSQL          │
│ └──────────┬───────────────┘  ④ Emit PaymentInitiated → Kafka          │
│            │ Kafka: payment.initiated                                   │
│            ▼                                                            │
│ ┌──────────────────────────┐                                            │
│ │  Service B               │  ⑤ Consume event (retry w/ backoff)       │
│ │  Ledger Service          │  ⑥ Atomic debit/credit (SERIALIZABLE tx)  │
│ │  :8081                   │  ⑦ Emit PaymentCompleted/Failed → Kafka   │
│ └──────────┬───────────────┘  ⑧ GET /v1/transactions/{userId}         │
│            │ Kafka: payment.completed                                   │
│            ▼                                                            │
│ ┌──────────────────────────┐                                            │
│ │  Service C (Bonus)       │  ⑨ Ingest into analytics table (OLAP)    │
│ │  Analytics Worker        │  ⑩ GET /v1/analytics/summary             │
│ │  :8082                   │                                            │
│ └──────────────────────────┘                                            │
└─────────────────────────────────────────────────────────────────────────┘

Infrastructure: PostgreSQL · Apache Kafka · Redis · Docker · Kubernetes
```

---

## Tech Stack

| Component         | Technology                        |
|-------------------|-----------------------------------|
| Language          | Java 21 (Virtual Threads ready)   |
| Framework         | Spring Boot 3.3                   |
| Database          | PostgreSQL 16                     |
| Messaging         | Apache Kafka 7.6 (Confluent)      |
| Caching           | Redis 7.2                         |
| Migrations        | Flyway                            |
| Documentation     | Swagger / OpenAPI 3               |
| Containerization  | Docker + Docker Compose           |
| Orchestration     | Kubernetes (Minikube compatible)  |
| CI/CD             | GitHub Actions                    |
| Load Testing      | K6                                |
| Testing           | JUnit 5 + Testcontainers          |

---

## Project Structure

```
swiftpay/
├── pom.xml                          # Parent Maven POM (multi-module)
├── common/                          # Shared DTOs, Events, Constants
├── transaction-gateway/             # Service A — REST API
├── ledger-service/                  # Service B — Kafka Consumer + Ledger
├── analytics-worker/                # Service C — OLAP Analytics
├── infra/
│   └── init-db.sql                  # PostgreSQL DB init script
├── k8s/                             # Kubernetes manifests
├── load-test/
│   └── k6-load-test.js              # K6 script: 250 TPS / 1M transactions
├── docker-compose.yml
└── .github/
    └── workflows/
        └── ci.yml                   # GitHub Actions pipeline
```

---

## Quick Start (Docker Compose)

### Prerequisites
- Docker 24+ and Docker Compose v2
- Java 21 + Maven 3.9 (for local build)

### 1. Clone and spin up the full stack
```bash
git clone https://github.com/your-org/swiftpay.git
cd swiftpay

# Start everything (PostgreSQL, Kafka, Redis, all 3 services)
docker-compose up --build
```

> First build takes ~3–5 minutes. Subsequent builds use layer cache.

### 2. Verify all services are healthy
```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Ledger
curl http://localhost:8082/actuator/health   # Analytics
```

### 3. Open Swagger UIs
| Service              | URL                                   |
|----------------------|---------------------------------------|
| Transaction Gateway  | http://localhost:8080/swagger-ui.html |
| Ledger Service       | http://localhost:8081/swagger-ui.html |
| Analytics Worker     | http://localhost:8082/swagger-ui.html |
| Kafka UI             | http://localhost:9000                 |

---

## API Reference

### POST /v1/payments — Initiate a Payment
```bash
curl -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "sender_id":   "550e8400-e29b-41d4-a716-446655440001",
    "receiver_id": "550e8400-e29b-41d4-a716-446655440002",
    "amount":      "100.00",
    "currency":    "USD"
  }'
```

**Response 202:**
```json
{
  "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "transaction_id": "your-idempotency-key",
  "sender_id": "550e8400-...",
  "receiver_id": "550e8400-...",
  "amount": 100.00,
  "currency": "USD",
  "status": "PENDING",
  "message": "Payment accepted and is being processed.",
  "created_at": "2024-01-15T10:30:00Z"
}
```

### GET /v1/transactions/{userId} — Transaction History
```bash
curl http://localhost:8081/v1/transactions/550e8400-e29b-41d4-a716-446655440001?page=0&size=20
```

### GET /v1/accounts/{userId}/balance — Account Balance
```bash
curl http://localhost:8081/v1/accounts/550e8400-e29b-41d4-a716-446655440001/balance
```

### GET /v1/analytics/summary — 24h Volume Summary
```bash
curl http://localhost:8082/v1/analytics/summary
```

### GET /v1/analytics/window?minutes=5 — Rolling TPS Window
```bash
curl "http://localhost:8082/v1/analytics/window?minutes=5"
```

---

## Seed Data

Flyway migration `V2__seed_accounts.sql` seeds **10 test accounts**, each with a $10,000.00 balance:

| User ID (UUID suffix)                           | Starting Balance |
|-------------------------------------------------|-----------------|
| `550e8400-e29b-41d4-a716-44665544000{1..10}`    | $10,000.00 USD  |

---

## Key Design Decisions

### Idempotency (Redis, 24h TTL)
- Client sends `Idempotency-Key` header (UUID).
- Gateway does `SET NX EX 86400` on key `idempotency:{key}`.
- On duplicate: returns the cached response immediately — **no double processing**.

### Atomic Debit/Credit (PostgreSQL SERIALIZABLE)
- LedgerService runs inside a `@Transactional(isolation = SERIALIZABLE)` block.
- Accounts locked with `SELECT ... FOR UPDATE` in consistent UUID order to prevent deadlocks.
- `@Version` optimistic locking on Account entity as a secondary guard.

### Kafka Retry with Exponential Backoff
- `DefaultErrorHandler` with `ExponentialBackOff(1s, factor=2, maxAttempts=3, maxInterval=10s)`.
- Message is **not acknowledged** on failure → Kafka retries automatically.

### Balance Caching Strategy
- Balance written to Redis (30s TTL) after every `PaymentCompleted` event.
- Gateway reads from cache first; falls back to `GET /v1/accounts/{userId}/balance`.
- Cache is evicted on `PaymentFailed` to force a fresh read next time.

### Deadlock Prevention
- Accounts always locked in ascending UUID order regardless of who is sender/receiver.

---

## Running Tests

```bash
# Unit tests only (fast, no containers)
mvn test -pl transaction-gateway,ledger-service

# Integration tests (Testcontainers — requires Docker)
mvn verify -Pfailsafe

# All tests
mvn verify
```

---

## Load Test (K6 — 250 TPS / 1M Transactions)

### Prerequisites
```bash
brew install k6        # macOS
# or: https://k6.io/docs/getting-started/installation/
```

### Run with PCAP capture
```bash
# 1. Start the stack
docker-compose up -d

# 2. Capture network traffic (in a separate terminal)
sudo tcpdump -i lo0 -w load-test/swiftpay-load.pcap port 8080 &

# 3. Run the load test
k6 run --out json=load-test/results.json load-test/k6-load-test.js

# 4. Stop tcpdump
sudo kill %1

# 5. View summary
cat load-test/summary.json
```

### Expected Results (tuned environment)
| Metric          | Target    |
|-----------------|-----------|
| Throughput      | ≥ 250 TPS |
| P95 Latency     | < 500ms   |
| P99 Latency     | < 1000ms  |
| Error Rate      | < 1%      |
| Business Success| > 98%     |

---

## Kubernetes (Minikube)

```bash
# Start Minikube
minikube start --memory=8192 --cpus=4

# Apply all manifests
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/postgres.yml
kubectl apply -f k8s/kafka.yml
kubectl apply -f k8s/redis.yml
kubectl apply -f k8s/ledger-service-deployment.yml
kubectl apply -f k8s/transaction-gateway-deployment.yml
kubectl apply -f k8s/analytics-worker-deployment.yml

# Watch pods come up
kubectl get pods -n swiftpay -w

# Access Gateway
minikube service transaction-gateway -n swiftpay --url
```

---

## Error Handling

| Scenario                       | HTTP Status | Kafka Behavior                          |
|--------------------------------|-------------|-----------------------------------------|
| Duplicate `Idempotency-Key`    | 202 (cached)| No new Kafka event                      |
| Insufficient funds (Gateway)   | 422         | No Kafka event (rejected early)         |
| Insufficient funds (Ledger)    | —           | `PaymentFailed` event emitted           |
| Kafka broker down              | 503         | Gateway rejects; Ledger retries 3× w/ backoff |
| DB constraint violation        | 500 + retry | Kafka consumer retries; logged + alertable |
| Account not found              | 404         | `PaymentFailed` event emitted           |

---

## Health Endpoints

```
GET /actuator/health          → Overall health (UP/DOWN)
GET /actuator/health/liveness → Liveness probe (K8s)
GET /actuator/health/readiness→ Readiness probe (K8s)
GET /actuator/metrics         → Micrometer metrics
GET /actuator/prometheus      → Prometheus scrape endpoint
```

---

## Submission Checklist

- [x] `docker-compose up --build` starts entire environment
- [x] POST /v1/payments end-to-end flow works
- [x] Insufficient funds handled at both Gateway and Ledger layers
- [x] Redis idempotency (24h TTL) implemented
- [x] Kafka retry with exponential backoff
- [x] Atomic debit/credit with SERIALIZABLE isolation
- [x] OpenAPI/Swagger docs on all three services
- [x] GitHub Actions CI (compile → unit test → integration test → Docker build)
- [x] K6 load test script (250 TPS / 1M transactions)
- [x] Kubernetes manifests (Minikube compatible)
- [x] Unit tests (Mockito) + Integration tests (Testcontainers)
- [x] Service C Analytics Worker (bonus)
