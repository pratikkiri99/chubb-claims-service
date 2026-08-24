# Chubb Claims Service

Backend for motor and property claims intake, lifecycle, workload, and outstanding liability.

## Prerequisites

- Java 21
- Docker (for local Postgres and Testcontainers)

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

- API: http://localhost:8080
- OpenAPI UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Tests

```bash
./mvnw verify
```

Uses Testcontainers PostgreSQL 16. No local database is required for the test suite.
