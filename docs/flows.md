# Flows

Happy paths and the state machine as implemented. APIs are listed at the end.

## Claim lifecycle

```mermaid
stateDiagram-v2
    [*] --> OPEN: POST /api/v1/claims\n(active policy)

    OPEN --> IN_PROGRESS: POST .../assignment\n(same-market staff)

    IN_PROGRESS --> PENDING_INFORMATION: POST .../information-requests
    PENDING_INFORMATION --> IN_PROGRESS: POST .../information

    IN_PROGRESS --> IN_PROGRESS: PATCH .../reserve
    IN_PROGRESS --> SETTLED: POST .../decision SETTLED
    IN_PROGRESS --> REJECTED: POST .../decision REJECTED

    SETTLED --> [*]
    REJECTED --> [*]
```

| From | Event | To | Actor |
|---|---|---|---|
| — | submit | `OPEN` | claimant |
| `OPEN` | assign | `IN_PROGRESS` | in-market staff |
| `IN_PROGRESS` | request info | `PENDING_INFORMATION` | assignee or in-market manager |
| `PENDING_INFORMATION` | provide info | `IN_PROGRESS` | claimant |
| `IN_PROGRESS` | update reserve | `IN_PROGRESS` | assignee or in-market manager |
| `IN_PROGRESS` | settle | `SETTLED` | assignee or in-market manager |
| `IN_PROGRESS` | reject | `REJECTED` | assignee or in-market manager |

Anything else is `409` (`illegal-claim-state` or `claim-already-assigned`). Writes on terminal claims are `409`. Cross-market staff is `403`. Missing/unknown `X-Staff-Id` on `/api/v1/staff/**` is `401`.

Assign is atomic (`UPDATE … WHERE status = OPEN AND assigned_staff_id IS NULL`) so two officers cannot both pick up the same claim.

## Claimant: report and track

```mermaid
sequenceDiagram
    actor Claimant
    participant API as ClaimantClaimController
    participant Svc as ClaimService
    participant DB as PostgreSQL
    participant Evt as ClaimEventPublisher

    Claimant->>API: POST /api/v1/claims
    API->>Svc: submit(command)
    Svc->>DB: load policy (number + market)
    alt missing / not ACTIVE / over sum insured / future incident
        Svc-->>API: DomainException
        API-->>Claimant: 404 / 422 / 400 ProblemDetail
    else eligible
        Svc->>DB: nextval(claim_number_seq)
        Svc->>DB: insert OPEN claim (reserve = claimed)
        Svc->>Evt: CLAIM_SUBMITTED
        API-->>Claimant: 201 Location /api/v1/claims/{claimNumber}
    end

    Claimant->>API: GET /api/v1/claims/{claimNumber}
    API-->>Claimant: 200 claimant DTO (no reserve, no staff)
```

## Staff: pickup, assess, decide

```mermaid
sequenceDiagram
    actor Officer
    participant API as StaffClaimController
    participant Svc as ClaimService
    participant DB as PostgreSQL
    participant Evt as ClaimEventPublisher

    Officer->>API: GET /queue  (X-Staff-Id)
    API-->>Officer: OPEN claims in officer market

    Officer->>API: POST /{claimNumber}/assignment
    API->>Svc: assignToSelf
    Svc->>DB: assignIfOpen
    alt 0 rows
        API-->>Officer: 409 already assigned
    else 1 row
        Svc->>Evt: CLAIM_ASSIGNED
        API-->>Officer: 200 IN_PROGRESS
    end

    Officer->>API: PATCH /{claimNumber}/reserve
    Note over Svc: only IN_PROGRESS; 0 ≤ reserve ≤ sumInsured

    alt settle
        Officer->>API: POST /decision SETTLED
        Svc->>Evt: CLAIM_DECIDED
        API-->>Officer: 200 SETTLED
    else reject
        Officer->>API: POST /decision REJECTED
        Svc->>Evt: CLAIM_DECIDED
        API-->>Officer: 200 REJECTED
    end
```

## Information round-trip

```mermaid
sequenceDiagram
    actor Officer
    actor Claimant
    participant StaffAPI as StaffClaimController
    participant ClaimAPI as ClaimantClaimController
    participant Svc as ClaimService

    Officer->>StaffAPI: POST .../information-requests { body }
    Svc-->>StaffAPI: PENDING_INFORMATION + INFORMATION_REQUEST
    Claimant->>ClaimAPI: GET /{claimNumber}
    ClaimAPI-->>Claimant: latestInformationRequest
    Claimant->>ClaimAPI: POST /{claimNumber}/information { body }
    Svc-->>ClaimAPI: IN_PROGRESS + INFORMATION_RESPONSE
    Officer->>StaffAPI: POST .../decision
```

## Manager: workload and exposure

```mermaid
flowchart TB
    subgraph Workload["GET /api/v1/staff/workload"]
        Q[incomingQueueCount — OPEN unassigned in market]
        M[myClaims — non-terminal assigned to caller]
        T[teamByOfficer — officers in team or, for MANAGER, market]
        P[performance — SETTLED/REJECTED in last 30 days]
    end

    subgraph Exposure["GET /api/v1/staff/exposure"]
        R["SUM(reserve) where status in OPEN, IN_PROGRESS, PENDING_INFORMATION"]
        B[Broken down by market and coverage type]
    end
```

Staff may only request their own market. Settled and rejected reserves are excluded from outstanding exposure; a newly submitted claim is included immediately (`reserve = claimedAmount`).

## Error mapping

```mermaid
flowchart LR
    E[Exception] --> H[GlobalExceptionHandler]
    H --> PD["ProblemDetail\ntype title status detail instance"]

    D[DomainException] --> H
    V[Bean Validation] --> H
    M[Missing X-Staff-Id] --> H
```

| Situation | Status | `type` suffix |
|---|---|---|
| Validation | 400 | `validation` |
| Unknown / inactive staff | 401 | `staff-unauthorized` |
| Wrong market / not assignee | 403 | `staff-forbidden` |
| Missing claim or policy | 404 | `claim-not-found` / `policy-not-found` |
| Illegal transition / already assigned | 409 | `illegal-claim-state` / `claim-already-assigned` |
| Lapsed policy / amount vs sum insured | 422 | `policy-not-active` / `reserve-limit` |
| Unexpected | 500 | `internal` (no stack in `detail`) |

`type` is `urn:chubb:claims:problem:{suffix}`.

## HTTP map

**Claimant** — no staff header

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/claims` | 201 + `Location` |
| GET | `/api/v1/claims/{claimNumber}` | 200 claimant view |
| POST | `/api/v1/claims/{claimNumber}/information` | 200 |

**Staff** — header `X-Staff-Id`

| Method | Path | Result |
|---|---|---|
| GET | `/api/v1/staff/claims/queue` | incoming OPEN |
| GET | `/api/v1/staff/claims/{claimNumber}` | full staff view |
| POST | `/api/v1/staff/claims/{claimNumber}/assignment` | pickup |
| POST | `/api/v1/staff/claims/{claimNumber}/information-requests` | ask claimant |
| PATCH | `/api/v1/staff/claims/{claimNumber}/reserve` | assess |
| POST | `/api/v1/staff/claims/{claimNumber}/decision` | settle or reject |
| GET | `/api/v1/staff/workload` | team load + performance |
| GET | `/api/v1/staff/exposure` | outstanding liability |

Seeded demo staff (see Flyway V5):

- Officer AU: `11111111-1111-1111-1111-111111111111`
- Second officer AU: `22222222-2222-2222-2222-222222222222`
- Manager AU: `33333333-3333-3333-3333-333333333333`
- Officer HK: `44444444-4444-4444-4444-444444444444`
