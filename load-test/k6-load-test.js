/**
 * SwiftPay Load Test — K6
 * Target: 250 TPS sustained, 1,000,000 total transactions
 *
 * Run:
 *   k6 run --out json=results.json load-test/k6-load-test.js
 *
 * With PCAP capture (requires tcpdump in parallel):
 *   sudo tcpdump -i lo -w load-test/swiftpay-load.pcap port 8080 &
 *   k6 run load-test/k6-load-test.js
 *   sudo kill %1
 */

import http   from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// ── Custom Metrics ────────────────────────────────────────────────────────────
const paymentSuccessCount    = new Counter("payment_success_total");
const paymentFailCount       = new Counter("payment_failure_total");
const insufficientFundsCount = new Counter("insufficient_funds_total");
const duplicateCount         = new Counter("duplicate_transaction_total");
const paymentDuration        = new Trend("payment_duration_ms", true);
const successRate            = new Rate("payment_success_rate");

// ── Test Config ───────────────────────────────────────────────────────────────
const TARGET_URL    = __ENV.GATEWAY_URL || "http://localhost:8080";
const TARGET_TPS    = 250;
const TOTAL_TX      = 1_000_000;
const DURATION_SECS = Math.ceil(TOTAL_TX / TARGET_TPS); // ~4000 seconds ≈ 67 min

// 10 test users (seeded by Flyway V2 in ledger-service)
const TEST_USERS = [
  "550e8400-e29b-41d4-a716-446655440001",
  "550e8400-e29b-41d4-a716-446655440002",
  "550e8400-e29b-41d4-a716-446655440003",
  "550e8400-e29b-41d4-a716-446655440004",
  "550e8400-e29b-41d4-a716-446655440005",
  "550e8400-e29b-41d4-a716-446655440006",
  "550e8400-e29b-41d4-a716-446655440007",
  "550e8400-e29b-41d4-a716-446655440008",
  "550e8400-e29b-41d4-a716-446655440009",
  "550e8400-e29b-41d4-a716-446655440010",
];

// ── Stage Ramp-Up Profile ─────────────────────────────────────────────────────
export const options = {
  scenarios: {
    sustained_load: {
      executor:   "constant-arrival-rate",
      rate:       TARGET_TPS,           // 250 iterations/sec
      timeUnit:   "1s",
      duration:   `${DURATION_SECS}s`,
      preAllocatedVUs: 300,
      maxVUs:          600,
    },
    ramp_up: {
      executor:   "ramping-arrival-rate",
      startRate:  10,
      timeUnit:   "1s",
      preAllocatedVUs: 50,
      maxVUs:     300,
      stages: [
        { target: 50,         duration: "30s"  },
        { target: TARGET_TPS, duration: "60s"  },
        { target: TARGET_TPS, duration: "120s" },
        { target: 0,          duration: "30s"  },
      ],
    },
  },

  thresholds: {
    http_req_duration:     ["p(95)<500", "p(99)<1000"], // 95th percentile < 500ms
    http_req_failed:       ["rate<0.01"],               // < 1% HTTP errors
    payment_success_rate:  ["rate>0.98"],               // > 98% business success
  },
};

// ── Virtual User Logic ────────────────────────────────────────────────────────
export default function () {
  // Pick two distinct random users
  const senderIdx   = Math.floor(Math.random() * TEST_USERS.length);
  let   receiverIdx = Math.floor(Math.random() * TEST_USERS.length);
  while (receiverIdx === senderIdx) {
    receiverIdx = Math.floor(Math.random() * TEST_USERS.length);
  }

  const payload = JSON.stringify({
    sender_id:   TEST_USERS[senderIdx],
    receiver_id: TEST_USERS[receiverIdx],
    amount:      (Math.random() * 9 + 1).toFixed(2), // $1 – $10
    currency:    "USD",
  });

  const headers = {
    "Content-Type":   "application/json",
    "Idempotency-Key": uuidv4(),
  };

  const start    = Date.now();
  const response = http.post(`${TARGET_URL}/v1/payments`, payload, { headers, timeout: "5s" });
  const elapsed  = Date.now() - start;

  paymentDuration.add(elapsed);

  const ok = check(response, {
    "status is 202": (r) => r.status === 202,
    "has payment id": (r) => {
      try { return JSON.parse(r.body).id !== undefined; }
      catch { return false; }
    },
  });

  if (response.status === 202) {
    paymentSuccessCount.add(1);
    successRate.add(1);
  } else if (response.status === 422) {
    insufficientFundsCount.add(1);
    successRate.add(0);
  } else if (response.status === 409) {
    duplicateCount.add(1);
    successRate.add(1); // duplicates are not failures
  } else {
    paymentFailCount.add(1);
    successRate.add(0);
  }
}

// ── Summary Teardown ──────────────────────────────────────────────────────────
export function handleSummary(data) {
  const summary = {
    total_requests:     data.metrics.http_reqs?.values?.count   || 0,
    success_count:      data.metrics.payment_success_total?.values?.count || 0,
    failure_count:      data.metrics.payment_failure_total?.values?.count || 0,
    insufficient_funds: data.metrics.insufficient_funds_total?.values?.count || 0,
    p50_ms: data.metrics.http_req_duration?.values?.["p(50)"] || 0,
    p95_ms: data.metrics.http_req_duration?.values?.["p(95)"] || 0,
    p99_ms: data.metrics.http_req_duration?.values?.["p(99)"] || 0,
    avg_tps: (data.metrics.http_reqs?.values?.rate || 0).toFixed(2),
  };

  console.log("\n========== SwiftPay Load Test Summary ==========");
  console.log(`Total Requests:      ${summary.total_requests}`);
  console.log(`Success Count:       ${summary.success_count}`);
  console.log(`Failure Count:       ${summary.failure_count}`);
  console.log(`Insufficient Funds:  ${summary.insufficient_funds}`);
  console.log(`P50 Latency:         ${summary.p50_ms.toFixed(1)}ms`);
  console.log(`P95 Latency:         ${summary.p95_ms.toFixed(1)}ms`);
  console.log(`P99 Latency:         ${summary.p99_ms.toFixed(1)}ms`);
  console.log(`Avg TPS:             ${summary.avg_tps}`);
  console.log("================================================\n");

  return {
    "load-test/summary.json": JSON.stringify(summary, null, 2),
    stdout: JSON.stringify(data, null, 2),
  };
}
