# E-Commerce Analytics with CQRS, Apache Kafka, and Kafka Streams

A production-oriented e-commerce analytics system built with the CQRS pattern, Spring Boot, PostgreSQL, Apache Kafka, and Kafka Streams. The platform separates write-side transactional workflows from read-side analytics so that product and order writes stay isolated from expensive aggregate queries, while Kafka Streams materializes queryable state stores for low-latency analytics APIs. [1][2]

## Architecture Overview

The solution uses two application services:

- **Command Service**: Handles write operations for products and orders, persists transactional data in PostgreSQL, and emits domain events to Kafka topics.
- **Query Service**: Consumes Kafka events, enriches and aggregates them with Kafka Streams, materializes state stores, and exposes analytics over REST through Interactive Queries. Kafka Streams supports direct querying of application state, which makes it a good fit for CQRS read models. [1][3]

### High-level flow

1. A client creates a product through the Command Service.
2. The Command Service stores the product in PostgreSQL and emits a `ProductCreated` event to `product-events`.
3. A client creates or updates an order through the Command Service.
4. The Command Service stores or updates the order in PostgreSQL and emits `OrderCreated` or `OrderUpdated` events to `order-events`.
5. The Query Service consumes both topics.
6. Kafka Streams builds a product table, joins order items with products, computes aggregates, and stores results in queryable state stores.
7. The Query Service exposes REST endpoints that query those stores directly. Interactive Queries are designed for this exact use case. [1][3]

## Repository Structure

```text
.
├── command-service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── query-service/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── seeds/
│   └── init.sql
├── tests/
├── docker-compose.yml
├── .env.example
├── pom.xml
└── README.md
```

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot |
| Database | PostgreSQL 14 |
| Messaging | Apache Kafka |
| Stream Processing | Kafka Streams |
| Containerization | Docker, Docker Compose |
| Testing | JUnit 5, Spring Boot Test, `kafka-streams-test-utils` |
| Coverage | JaCoCo |

Kafka Streams testing is expected to use `TopologyTestDriver`, which can execute a topology synchronously without a live Kafka cluster, making it well suited for deterministic stream-topology tests. [4][5]

## Core Design

### CQRS

The Command Service owns the write model and PostgreSQL tables. The Query Service owns the read model and precomputed materialized views derived from Kafka events. This separation keeps query workloads from impacting write-side performance. [3][1]

### Event-driven model

Events are published after successful command-side persistence. The project uses versionable JSON envelopes with fields such as `eventId`, `eventType`, `schemaVersion`, and `timestamp` to support traceability and future schema evolution.

### Exactly-once processing

Kafka Streams should be configured with `processing.guarantee=exactly_once_v2` for resilient stream processing and transactional updates to output topics and state store changelogs. Spring Kafka documentation notes that EOS v2 is the supported model in current Spring Kafka versions, and Kafka Streams uses exactly-once guarantees to ensure atomic processing and writes. [2][6]

## Services

## Command Service

The Command Service runs on port `8080` and exposes write APIs for products and orders. It persists data to PostgreSQL and publishes events to Kafka topics.

### Database schema

The PostgreSQL write model contains two required tables.

#### `products`

| Column | Type | Constraints |
|---|---|---|
| id | SERIAL | PRIMARY KEY |
| name | VARCHAR(255) | NOT NULL |
| category | VARCHAR(100) | NOT NULL |
| price | DECIMAL(10,2) | NOT NULL |
| created_at | TIMESTAMP | DEFAULT NOW() |

#### `orders`

| Column | Type | Constraints |
|---|---|---|
| id | SERIAL | PRIMARY KEY |
| customer_id | INTEGER | NOT NULL |
| status | VARCHAR(50) | NOT NULL |
| items | JSONB | NOT NULL |
| created_at | TIMESTAMP | DEFAULT NOW() |

These tables should be created automatically through the SQL files mounted from the `seeds/` directory into the PostgreSQL container.

### Command API

#### Create product

**Endpoint**

```http
POST /api/products
```

**Request**

```json
{
  "name": "Laptop",
  "category": "Electronics",
  "price": 1200.00
}
```

**Response**

```json
{
  "id": 1,
  "name": "Laptop",
  "category": "Electronics",
  "price": 1200.00
}
```

**Kafka event**

- Topic: `product-events`
- Key: `productId`
- Event type: `ProductCreated`

#### Create order

**Endpoint**

```http
POST /api/orders
```

**Request**

```json
{
  "customerId": 101,
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "price": 1200.00
    }
  ]
}
```

**Response**

```json
{
  "id": 1,
  "customerId": 101,
  "status": "CREATED",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "price": 1200.00
    }
  ]
}
```

**Kafka event**

- Topic: `order-events`
- Key: `orderId`
- Event type: `OrderCreated`

#### Update order status

**Endpoint**

```http
PUT /api/orders/{orderId}/status
```

**Request**

```json
{
  "status": "PAID"
}
```

**Response**

```json
{
  "id": 1,
  "customerId": 101,
  "status": "PAID",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "price": 1200.00
    }
  ]
}
```

**Kafka event**

- Topic: `order-events`
- Key: `orderId`
- Event type: `OrderUpdated`

## Query Service

The Query Service runs on port `8081` and hosts the Kafka Streams application and analytics API.

### Kafka Streams topology goals

The topology should:

- Build a `KTable` from `product-events` keyed by product ID.
- Consume `order-events` as a `KStream`.
- For `OrderCreated` events, flatten each order into line-item events.
- Join line items with the product table to enrich them with category and product metadata.
- Aggregate product-level sales into `product-sales-store`.
- Aggregate category-level revenue into `category-revenue-store`.
- Aggregate 1-hour tumbling-window revenue with a grace period into `hourly-sales-store`.
- Expose a topology description endpoint using `topology.describe()`.

### Interactive Queries

Kafka Streams natively supports querying local state stores, which enables the Query Service to expose analytics without copying results into a separate read database. Confluent’s documentation also notes that Interactive Queries can be used to access application state and, in newer APIs, structured query-based access is available through IQv2. [1][7]

### Analytics API

#### Product sales

```http
GET /api/analytics/products/{productId}/sales
```

**Response**

```json
{
  "productId": 1,
  "totalSales": 2400.0
}
```

#### Category revenue

```http
GET /api/analytics/categories/{categoryName}/revenue
```

**Response**

```json
{
  "category": "Electronics",
  "totalRevenue": 2400.0
}
```

#### Hourly sales

```http
GET /api/analytics/hourly-sales?start=2026-07-17T00:00:00Z&end=2026-07-18T00:00:00Z
```

**Response**

```json
[
  {
    "windowStart": "2026-07-17T10:00:00Z",
    "windowEnd": "2026-07-17T11:00:00Z",
    "totalSales": 2400.0
  }
]
```

#### Topology description

```http
GET /api/analytics/topology
```

**Response**

A text or JSON representation of `topology.describe()` showing sources, processors, state stores, and sinks.

## Environment Variables

Create a local `.env` file based on `.env.example`.

```env
# PostgreSQL Configuration
DB_HOST=db
DB_PORT=5432
POSTGRES_DB=analytics_db
POSTGRES_USER=user
POSTGRES_PASSWORD=password

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
PRODUCT_EVENTS_TOPIC=product-events
ORDER_EVENTS_TOPIC=order-events
ENRICHED_ORDER_EVENTS_TOPIC=enriched-order-events
KAFKA_STREAMS_APP_ID=ecommerce-analytics-query
KAFKA_STREAMS_HOST=query-service
KAFKA_STREAMS_PORT=8081

# Service Ports
COMMAND_SERVICE_PORT=8080
QUERY_SERVICE_PORT=8081

# Query Service Stores
PRODUCT_SALES_STORE=product-sales-store
CATEGORY_REVENUE_STORE=category-revenue-store
HOURLY_SALES_STORE=hourly-sales-store
```

## Docker Compose

The full stack is orchestrated with Docker Compose and includes all required services: `zookeeper`, `kafka`, `db`, `command-service`, and `query-service`. PostgreSQL should include a health check based on `pg_isready`, and the Command Service should wait for the database to become healthy before startup. [1]

### Start the full platform

```powershell
docker compose up --build -d
```

### Inspect running containers

```powershell
docker compose ps
```

### Follow logs

```powershell
docker compose logs -f db
docker compose logs -f kafka
docker compose logs -f command-service
docker compose logs -f query-service
```

### Stop the platform

```powershell
docker compose down
```

### Reset containers and volumes

```powershell
docker compose down -v
```

## Local Development in VS Code

1. Open the repository in VS Code.
2. Install Java Extension Pack and Docker extension.
3. Make sure Java 17, Maven, Docker Desktop, and Git are installed.
4. Import the Maven projects when prompted.
5. Run tests before bringing up the full stack.
6. Use the integrated PowerShell terminal for API calls and Docker commands.

### Suggested build order

1. Complete the Command Service persistence and publishing flow.
2. Verify DB schema and Kafka events.
3. Implement the Kafka Streams topology.
4. Add Interactive Query REST endpoints.
5. Add topology tests using `TopologyTestDriver`.
6. Add coverage and finalize Docker execution.

## Build and Test

### Run all tests

```powershell
mvn clean test
```

### Generate coverage

JaCoCo reports are generated during `mvn test` under:

- `command-service/target/site/jacoco/index.html`
- `query-service/target/site/jacoco/index.html`

The stream-topology tests should use `kafka-streams-test-utils`, and the recommended entry point is `TopologyTestDriver`, which runs synchronously and does not require a live Kafka cluster. [4][5]

## Verification Commands

### Verify PostgreSQL schema

```powershell
docker exec -it cqrs_db psql -U user -d analytics_db -c "\dt"
docker exec -it cqrs_db psql -U user -d analytics_db -c "SELECT * FROM products;"
docker exec -it cqrs_db psql -U user -d analytics_db -c "SELECT * FROM orders;"
```

### Create a product

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/products" `
  -ContentType "application/json" `
  -Body '{"name":"Laptop","category":"Electronics","price":1200.00}'
```

### Create an order

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/orders" `
  -ContentType "application/json" `
  -Body '{"customerId":101,"items":[{"productId":1,"quantity":2,"price":1200.00}]}'
```

### Update order status

```powershell
Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8080/api/orders/1/status" `
  -ContentType "application/json" `
  -Body '{"status":"PAID"}'
```

### Read product-sales analytics

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/analytics/products/1/sales"
```

### Read category revenue analytics

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/analytics/categories/Electronics/revenue"
```

### Read hourly sales analytics

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/analytics/hourly-sales?start=2026-07-17T00:00:00Z&end=2026-07-18T00:00:00Z"
```

### View Kafka topics

```powershell
docker exec -it kafka kafka-topics --bootstrap-server kafka:9092 --list
```

### Consume product events

```powershell
docker exec -it kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic product-events --from-beginning
```

### Consume order events

```powershell
docker exec -it kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-events --from-beginning
```

## Troubleshooting

### `Unable to connect to the remote server`

This usually means the Command Service is not listening on `localhost:8080`, the container failed to start, or startup is still in progress. Check container status with `docker compose ps`, then inspect service logs with `docker compose logs -f command-service`. If the service is missing, rebuild the stack with `docker compose up --build -d`. [1]

### Command Service not starting

Check the following:

- PostgreSQL health check passed.
- Kafka health check passed.
- Spring Boot application started successfully.
- Port `8080` is not already in use.
- Docker build succeeded for the service image.

Useful commands:

```powershell
docker compose ps
docker compose logs -f command-service
docker compose logs -f db
docker compose logs -f kafka
```

### Query Service store not queryable yet

Kafka Streams state stores are not always available immediately at startup. Wait until the application reaches a running state and the topology has restored any local state before calling analytics endpoints. Interactive Queries operate against the running state stores of the application. [1][7]

### Tests pass but runtime fails

Topology tests with `TopologyTestDriver` validate stream logic without a real cluster, but runtime integration can still fail because of broker connectivity, Docker networking, or application configuration drift between test and container environments. [4][5]

## Implementation Notes

- Use JSON event envelopes with schema versioning.
- Keep idempotency in mind for write operations.
- Prefer explicit DTOs for Kafka payloads rather than loosely structured maps.
- Add DLQ handling for malformed or poison-pill events in later hardening iterations.
- Keep store names centralized in configuration to avoid drift between topology and controller code.
- Persist only the write model in PostgreSQL; keep read-side analytics in Kafka Streams materialized views.

## Submission Checklist

- `docker-compose.yml` defines all five required services.
- `.env.example` documents all environment variables.
- `seeds/init.sql` creates the schema automatically.
- Command Service exposes product and order write APIs.
- Query Service exposes product, category, hourly, and topology analytics APIs.
- Kafka Streams uses `processing.guarantee=exactly_once_v2`. [2]
- Kafka Streams topology is tested using `TopologyTestDriver`. [4][5]
- Unit tests and coverage reports are included.
- `README.md` documents setup, architecture, APIs, and validation commands.