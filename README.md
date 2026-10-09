# E-commerce microservices backend

Five independently built services: Customer, Catalog & Inventory, and Order in Java 21 / Spring Boot 4.1.1; Payment and Notification in Node.js 24 / Express. Each owns a PostgreSQL database and Flyway migrations. Docker Compose runs the application and an optional Prometheus/Loki/Tempo monitoring stack. There is no frontend or Kubernetes configuration.

This is a runnable local development backend. Payment processing is a deliberately simulated, persistent sandbox; notifications are a durable customer inbox. No real card charge, email or SMS is sent.

## Start

Prerequisites: Docker with Compose v2, Python 3. Allow several GB for first-time images and build caches, and at least 4 GB of Docker memory (6 GB recommended for first-time builds) for the complete stack.

```bash
python3 scripts/init_env.py
docker compose --profile monitoring up -d --build
```

On an 8 GB host running other applications, use the lighter development mode when memory is constrained:

```bash
docker compose -f compose.yaml -f compose.light.yaml up -d --build
```

This mode retains authentication, all five services, separate databases and RabbitMQ. It disables trace/log export and omits optional monitoring containers; application metrics and health endpoints still work. When switching from a running full stack, stop its monitoring containers or run `docker compose --profile monitoring down` first (volumes are retained).

The first build downloads Maven/npm dependencies and container images. The two Node database migration jobs use a small native Java Flyway runner; no architecture emulation is required. Spring services apply migrations at startup and validate their JPA schema. Compose enables an init process for signal forwarding and child-process reaping. Applications and databases have separate containers; persistent data lives in named volumes.

```bash
docker compose --profile monitoring ps
docker compose logs -f order-service
docker compose --profile monitoring down
```

`down` preserves data. Do not add `-v` unless you intend to erase this application's databases, identity provider, broker and monitoring history. Recreating `.env` while retaining old database volumes will invalidate their existing credentials.

Local endpoints:

| Component | URL |
|---|---|
| Public API gateway | http://localhost:18080 |
| Keycloak | http://localhost:8180 |
| Customer / Catalog / Order health and metrics | http://localhost:18101 / 18102 / 18103 |
| Payment / Notification health and metrics | http://localhost:18104 / 18105 |
| Prometheus | http://localhost:19090 |
| Loki API | http://localhost:13100 |
| Tempo API | http://localhost:13200 |
| Collector OTLP HTTP | http://localhost:14318 |

Host ports bind to localhost. Databases and RabbitMQ have no published ports. Each application has liveness at `/actuator/health/liveness`, readiness at `/actuator/health/readiness`, and health at `/actuator/health`. Node responses use the same `{"status":"UP"}` / `DOWN` shape as Spring, so the current engine's health adapter can probe them. Readiness checks the service database; Notification additionally checks its broker consumer. Checkout dependency outages do not make the Order process's liveness fail.

## Authentication

Keycloak imports a local-only realm. The public client `ecommerce-cli` permits password grants solely for local API testing:

| User | Password | Role |
|---|---|---|
| customer | customer-local-only | customer |
| other | other-local-only | customer |
| admin | admin-local-only | admin |

```bash
curl -s http://localhost:8180/realms/ecommerce/protocol/openid-connect/token \
  -d grant_type=password -d client_id=ecommerce-cli \
  -d username=customer -d password=customer-local-only
```

Use the returned `access_token` in `Authorization: Bearer ...`. Tokens must have the configured issuer, valid signature/expiry and `ecommerce-api` audience. Customer IDs come from the authenticated subject. Public catalog reads need no login; writes/restocking and refunds require the admin role. Internal inventory/payment endpoints require the generated `X-Service-Key`; the gateway does not route internal APIs and strips this header on public requests.

These demo accounts, password grants, shared internal key, Keycloak development mode, and plain HTTP are local choices. Production needs HTTPS, an appropriate OIDC flow/client, managed secrets, service identities and hardened identity storage. Changing imported realm configuration requires managing the existing realm or starting with a fresh local identity volume; imports do not overwrite an existing realm.

## Public API

The static OpenAPI contract is `contracts/openapi.json`.

| Method / path | Purpose |
|---|---|
| GET / PUT `/api/customers/me` | Read/create/update your profile |
| GET `/api/products?page=0&size=20` | Browse products |
| GET `/api/products/{id}` | Product and available stock |
| POST `/api/products` | Admin creates a product |
| PUT `/api/products/{id}` | Admin changes product details/price |
| POST `/api/products/{id}/restock` | Admin adds stock |
| GET / PUT `/api/cart` | Read/replace your cart |
| POST `/api/orders` | Submit checkout with `Idempotency-Key` |
| GET `/api/orders?page=0` | Your order history |
| GET `/api/orders/{id}` | Your order and processing status |
| POST `/api/orders/{id}/cancel` | Request cancellation while still RECEIVED |
| POST `/api/orders/{id}/refund` | Admin requests a full sandbox refund |
| GET `/api/payments/me?page=0` | Your sandbox payment history |
| GET `/api/notifications/me?page=0` | Your durable inbox |

Example product body (admin):

```json
{"sku":"BOOK-001","name":"Example Book","description":"Paperback","price":"149.99","currency":"ZAR","stock":10}
```

Cart body:

```json
{"items":[{"productId":"PRODUCT_UUID","quantity":2}]}
```

Checkout body:

```json
{"address":"Your delivery address","paymentToken":"sandbox-success"}
```

Send a unique 8–100 character `Idempotency-Key` (letters, digits, `_`, `-`). Checkout and cancellation return HTTP 202 with an order ID; poll the order endpoint for a terminal state. Use `sandbox-decline` to exercise rejection and stock release. Totals are priced by Catalog on reservation, never accepted from the client. Only ZAR is supported; shipping and tax are zero in this local model. Order snapshots preserve purchased prices/names, and the delivery address is stored privately.

## Boundaries and failure behavior

- Customer owns profiles only; it calls none of the other four services.
- Catalog owns products, prices and inventory; it calls none of the other four services.
- Order owns carts, order snapshots and durable checkout state. It calls Catalog and Payment only in its background checkout/refund worker. Reading history or saving a cart does not call them.
- Payment owns sandbox payment/refund records and calls none of the other four services.
- Notification consumes durable order events through RabbitMQ; it does not call Order, Customer or Payment.

Each service uses its own database credentials and never queries another service's tables. Separate databases do not have cross-service foreign keys. Java packages separate controllers, DTOs, models, repositories, services, mappers, exceptions, configuration and messaging. Node follows equivalent boundaries. API responses use DTOs rather than Java entities. Inputs are validated, list endpoints are bounded and JPA open-session-in-view is disabled.

Checkout transitions are `RECEIVED → RESERVED → PAID → CONFIRMED`. A decline transitions through `DECLINED`, releases inventory, then reaches `REJECTED`. Insufficient stock rejects before payment. Admin refunds transition `CONFIRMED → REFUND_PENDING → REFUNDED`; a refund does not automatically return shipped goods to stock. Physical returns/restocking are separate admin operations.

The worker persists one saga step per transaction and retries transient failures with bounded backoff. Inventory reservations and payments use the order UUID for idempotency. Database locks prevent overselling and concurrent duplicate processing. Product locks use deterministic ordering. A successful downstream operation with a lost response is safely retried. Cancellation transitions `RECEIVED → CANCEL_PENDING → CANCELLED` after releasing any inventory hold. Catalog records a release tombstone even when no hold exists, so a delayed reservation request cannot create an orphan hold after cancellation. Terminal order events are recorded in the same transaction as the order state, published with broker confirms, and deduplicated by Notification before acknowledgement. Invalid events go to a durable dead-letter queue.

If RabbitMQ or Notification is unavailable, confirmed orders remain confirmed and event delivery resumes later. If Catalog or Payment is unavailable, checkout waits; profiles and unrelated catalog operations remain available. Dependencies apply to specific operations, not every endpoint in a service.

Durable unresolved reservations intentionally remain held while payment outcome is uncertain; automatic expiry is not implemented. This favors avoiding an unsafe release after an uncertain charge. A production provider integration needs webhook verification, reconciliation, reservation deadlines/compensation, and operational handling of permanently stuck orders. Tax/shipping, discounts, categories, fulfillment and partial refunds are further business features, not implemented claims.

## Monitoring and Blast Radius integration

Start with `--profile monitoring` for all telemetry backends and PostgreSQL exporters. The Java OpenTelemetry agent and Node automatic instrumentation produce HTTP, database and messaging spans, and correlated application logs. Prometheus scrapes service/runtime metrics, PostgreSQL exporters and RabbitMQ. There is no SigNoz installation or dashboard duplication. Container/host CPU monitoring is not included; application runtimes and database/broker metrics are included.

Identities are stable: `customer-service`, `catalog-service`, `order-service`, `payment-service`, `notification-service`; database IDs use `customer-db`, etc. Metrics include `service`, `application=ecommerce`, `environment=local`. Traces/logs include `service.name`, `application` and `deployment.environment.name=local` (Loki indexes this as `deployment_environment_name`, matching the current engine).

To connect your existing engine without changing its repository, first start this stack, then from the engine repository:

```bash
export ECOMMERCE_ROOT=/absolute/path/to/Ecommerce
docker compose -f docker-compose.yml \
  -f "$ECOMMERCE_ROOT/infrastructure/blast-radius/compose.override.yaml" \
  up -d --build
```

The override joins only the engine backend to the external `ecommerce-network`, mounts the supplied profile, and selects the ecommerce/local application. Its existing database and frontend remain on the engine's default network. This changes that deployment's monitored application; the current engine supports one application/environment per deployment. The integration template has been checked against the local engine configuration, but live engine incident/topology acceptance is a separate verification step.

Run normal checkout traffic to exercise dependency edges before investigating outages. Tempo-derived topology cannot discover unused paths, and broker/database identity normalization needs verification in the engine. `contracts/topology.json` records declared dependencies and operation-specific impact. It is a design contract, not an automatically imported or observed engine graph. The current engine can directly probe all five service liveness endpoints; independently identifying all five database outages requires extending its single JDBC-target adapter. Metrics such as scrape failures are supporting evidence, not direct proof of process death.

## Verification

```bash
mvn verify
npm --prefix payment-service test
npm --prefix notification-service test
python3 scripts/smoke.py
python3 scripts/smoke.py --outages
python3 scripts/check_telemetry.py
```

Smoke checks create uniquely named products and persisted orders. `--outages` deliberately stops/restarts only this project's Catalog and Payment containers, verifies unrelated services still work, then verifies pending checkout recovers. It does not touch other Docker projects or databases. Unit tests exercise uncertain payment outcomes and failed stock-release retries. Telemetry checks validate all service metric targets, per-service logs/traces and cross-service checkout traces.

See `VERIFICATION.md` for the checks completed during implementation and their limits.
