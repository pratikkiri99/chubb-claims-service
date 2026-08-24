# Architecture

This service is the backend for Chubb APAC motor and property claims: intake, lifecycle, staff workload, and outstanding liability. Frontend is out of scope.

## Context

```mermaid
flowchart LR
    Claimant -->|"REST /api/v1/claims"| SVC[claims-service]
    Officer -->|"REST /api/v1/staff/*\nX-Staff-Id"| SVC
    Manager -->|"workload + exposure"| SVC
    SVC --> PG[(PostgreSQL 16)]
    SVC -->|"ClaimEventPublisher\nlogging adapter today"| Downstream["Notification / warehouse\n(not built)"]
```

**Why one service:** a 2–3 hour slice needs one transactional boundary for “create claim + default reserve + event”. Package boundaries (`policy`, `staff`, `claim`, `workload`, `exposure`) are the internal seams. A production landscape would split Policy, Identity, Notification, and Payments.

## Containers (runtime)

```mermaid
flowchart LR
    subgraph Clients
        UI["HTTP clients / Swagger UI"]
    end

    subgraph "claims-service :8080"
        API["REST /api/v1"]
        SVC["Domain services"]
        EVT["ClaimEventPublisher"]
    end

    PG[(PostgreSQL 16)]
    LOG["Application logs\n(Kafka-ready topics documented, broker not in this timebox)"]

    UI --> API
    API --> SVC
    SVC --> PG
    SVC --> EVT
    EVT --> LOG
```

| Concern | Choice |
|---|---|
| Sync user waits | REST (submit, track, assign, decide, workload, exposure) |
| Async side effects | `ClaimEventPublisher` port; `LoggingClaimEventPublisher` bean |
| Schema | Flyway only; JPA `ddl-auto: validate`; `open-in-view: false` |
| Auth (assessment) | Claimant: knowledge of `claimNumber`. Staff: `X-Staff-Id` matching an active `staff` row |

Intended Kafka topics (not implemented): `chubb.claims.claim-submitted.v1`, `…assigned.v1`, `…information-requested.v1`, `…information-provided.v1`, `…decided.v1`. Key: `claimNumber`.

## Components (inside the jar)

```mermaid
flowchart TB
    subgraph http["HTTP adapter"]
        CC["ClaimantClaimController"]
        SC["StaffClaimController"]
        WC["WorkloadController"]
        EC["ExposureController"]
        ADV["GlobalExceptionHandler\nRFC 7807 ProblemDetail"]
        MAP["ClaimMapper — DTOs only"]
    end

    subgraph domain["Application / domain"]
        CS["ClaimService"]
        WS["WorkloadService"]
        ES["ExposureService"]
        AGG["Claim aggregate\nstate machine"]
    end

    subgraph persist["Persistence"]
        CR["ClaimRepository"]
        PR["PolicyRepository"]
        SR["StaffRepository"]
        SEQ["claim_number_seq"]
    end

    CC --> CS
    SC --> CS
    WC --> WS
    EC --> ES
    CS --> AGG
    CS --> CR
    CS --> PR
    CS --> SR
    CS --> SEQ
    WS --> CR
    WS --> SR
    ES --> CR
    ES --> SR
    ADV -.-> CC
```

Layering rules:

- Controllers map HTTP ↔ commands/DTOs. No state-machine `if` in controllers.
- JPA entities never leave the service layer.
- Transitions live on `Claim` (`open`, `assign`, `requestInformation`, `provideInformation`, `updateReserve`, `settle`, `reject`).
- Illegal transitions and authz become `DomainException` → `urn:chubb:claims:problem:{suffix}`.

## Data model

```mermaid
erDiagram
    POLICY ||--o{ CLAIM : covers
    STAFF ||--o{ CLAIM : "assigned to"
    CLAIM ||--o{ CLAIM_COMMUNICATION : has
    STAFF ||--o{ CLAIM_COMMUNICATION : authors

    POLICY {
        uuid id PK
        string policy_number
        string market
        string coverage_type
        numeric sum_insured
        string status
    }
    STAFF {
        uuid id PK
        string email
        string market
        string team
        string role
    }
    CLAIM {
        uuid id PK
        string claim_number UK
        string status
        numeric claimed_amount
        numeric reserve_amount
        numeric settlement_amount
        string rejection_reason
    }
    CLAIM_COMMUNICATION {
        uuid id PK
        string kind
        string author_type
        text body
    }
```

Invariants are encoded twice: CHECK/UNIQUE/FK in Flyway, and the `Claim` aggregate in Java. Outstanding liability is `SUM(reserve_amount)` where status ∈ {`OPEN`, `IN_PROGRESS`, `PENDING_INFORMATION`}. Terminal claims drop out of exposure; the last reserve is kept for audit.

## Package map

```
com.chubb.claims
  claim/       aggregate, service, REST for lifecycle
  policy/      reference data (seeded; no write API)
  staff/       officers and managers
  workload/    team load + 30-day performance
  exposure/    outstanding reserve rollup
  event/       publisher port
  shared/      DomainException, ProblemDetail
```

## What a later split would look like

```mermaid
flowchart LR
    GW[API gateway / IdP] --> CLAIMS[claims-service]
    CLAIMS --> POL[policy-service]
    CLAIMS --> K[(Kafka)]
    K --> N[notification-service]
    K --> WH[warehouse]
    CLAIMS --> PAY[payments — after SETTLED]
```

Not in this repo: OAuth, document upload, FNOL adapters, payments, outbox, CQRS read models.
