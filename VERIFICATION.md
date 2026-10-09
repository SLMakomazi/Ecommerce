# Verification status

## Completed

- Maven `verify`: all Java modules built; four checkout-worker tests passed. These cover uncertain payment responses, failed compensation retries and cancellation retries.
- Node tests: three payment tests and one notification test passed.
- Docker Compose configuration validated; all five application images built and ran with native containers. All five PostgreSQL schemas migrated successfully.
- Live integration smoke test passed: authentication, resource ownership, customer profiles, catalog administration, checkout, exact monetary totals, idempotency, refunds, declined-payment compensation, notification delivery, concurrent inventory reservation and cancellation tombstones.
- Live outage test passed: stopping Catalog and Payment independently left unrelated services available; pending checkout recovered after restart without duplicate payment.

The live business checks used `compose.yaml` with `compose.light.yaml`. The services are currently running in this mode, with application health and metrics endpoints available.

## Remaining runtime acceptance

The full monitoring acceptance script did **not** pass: it timed out waiting for metric targets while the local host was under severe resource pressure. The 8 GB host was using approximately 8 GB of swap, and Java reported thread starvation. Optional monitoring containers were stopped to complete the business checks. Configuration and instrumentation are included, but complete metrics/logs/traces collection and cross-service trace propagation still require a successful runtime check.

On a host with sufficient available memory, run:

```bash
docker compose --profile monitoring down
docker compose --profile monitoring up -d --build
python3 scripts/smoke.py
python3 scripts/check_telemetry.py
```

`down` retains named volumes. Do not use `-v` to preserve existing data.

The Blast Radius profile and Compose override were checked against the local engine configuration. The engine was not deployed against this stack; incident detection and observed topology are not yet verified. The declared topology JSON does not automatically populate its graph. Its current single JDBC-target adapter also needs extension to probe all five databases independently.

## Scope

Payments use persistent sandbox outcomes; notifications use a durable database inbox. Production payment providers, email/SMS delivery, tax/shipping calculation, inventory reconciliation for indefinitely uncertain payments and production deployment hardening remain outside this implementation.
