# PRP: Chubb APAC Claims Service

**Status:** Ready for implementation  
**Stack (locked):** Java 21 · Spring Boot 3.5.x · Maven · PostgreSQL 16 · Flyway · Spring Data JPA · springdoc-openapi · Testcontainers · JUnit 5 · Mockito · AssertJ  
**Out of scope for this document:** writing production code. Execute this PRP milestone-by-milestone. Do not re-read `requirement/candidate_assessment_brief.md` for behaviour — every functional rule is restated below as a checkable criterion.

---

## Goal

A single locally runnable Spring Boot service (`claims-service`) that lets a claimant report a motor or property incident against an existing policy, track the claim, supply extra information when asked, and see the eventual decision; and that lets claims staff pick up incoming work, review/assess a claim (including setting a reserve), settle or reject it, and query team workload/performance plus outstanding liability exposure. Every write goes through a documented state machine, every invariant is enforced in both PostgreSQL constraints and domain services, every error is an RFC 7807 `ProblemDetail`, and `./mvnw verify` is green on Testcontainers PostgreSQL.

---

## Why

Chubb APAC today takes motor and property claims across six markets by phone and email. Claimants have no visibility. Staff work from shared inboxes and spreadsheets. Managers have no live view of outstanding claims or reserved liability. This service is the backend for that platform: one source of truth for the claim lifecycle, the incoming queue, assignment, decisions, and exposure. Frontend is out of scope; API shape, service boundary, and data model are the product.

This is a sprint-format assessment (target 2–3 hours, hard cap 5). The scope below is a ruthlessly prioritised vertical slice: the lifecycle and the two management reads (workload, exposure) built correctly, rather than a platform sketch. Everything explicitly deferred is listed under **Out of scope** and **Open questions**.

---

## Stack and conventions (locked)

| Concern | Rule |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5.x (latest 3.5 patch) |
| Build | Maven Wrapper (`./mvnw`). Packaging: `jar`. Group: `com.chubb`. Artifact: `claims-service`. Version: `0.1.0-SNAPSHOT` |
| Database | PostgreSQL 16. Local: `docker-compose.yml`. Tests: Testcontainers `postgres:16-alpine` + Spring Boot `@ServiceConnection` |
| Schema | Flyway only. `spring.jpa.hibernate.ddl-auto: validate`. Never `update` / `create-drop` |
| Migrations | `src/main/resources/db/migration/V{n}__snake_case.sql` (integer versions, double-underscore, snake_case description). Never edit an applied migration; add `V{n+1}` |
| Persistence | Spring Data JPA. All associations `FetchType.LAZY`. `spring.jpa.open-in-view: false` |
| IDs | Entity PKs are `UUID`, generated with `@GeneratedValue(strategy = GenerationType.UUID)`. **Never** `GenerationType.IDENTITY`, **never** `BIGSERIAL` / `GENERATED … AS IDENTITY`. Human-readable `claim_number` comes from PostgreSQL `SEQUENCE claim_number_seq` |
| API | REST/JSON, context path none, prefix `/api/v1`. DTOs (Java records) at the HTTP boundary; JPA entities never leave the service layer |
| Validation | `jakarta.validation` on request records. Bean Validation groups not required |
| Errors | RFC 7807 `ProblemDetail` from a single `@RestControllerAdvice`. No ad-hoc error JSON |
| API docs | springdoc-openapi (`/v3/api-docs`, `/swagger-ui.html`) |
| JSON | ISO-8601 instants (UTC), `LocalDate` as `yyyy-MM-dd`, enums as strings, money as JSON numbers backed by `BigDecimal` scale 2 |
| Money | `BigDecimal`, column `NUMERIC(19,2)`, Java scale 2 `RoundingMode.HALF_UP` |
| Time | `Instant` + `OffsetDateTime` JSON in UTC. Incident date is `LocalDate` (no time zone) |
| Lombok | Allowed on entities (`@Getter @Setter @NoArgsConstructor`). **Forbidden:** `@Data` / `@EqualsAndHashCode` on entities (equals/hashCode = id only, after persist). DTOs are records — no Lombok |
| Tests | JUnit 5 + Mockito + AssertJ. Tests are written **inside** the same milestone as the production code they prove. Never deferred |
| Package | Feature-based: `com.chubb.claims.<feature>…` (see layout below) |
| Transactions | `@Transactional` on service write methods; `@Transactional(readOnly = true)` on queries. Controllers are not transactional |

### Package layout

```
com.chubb.claims
  ClaimsServiceApplication
  config/          OpenApiConfig, JacksonConfig (if needed)
  shared/
    api/           GlobalExceptionHandler, ProblemTypes
    error/         domain exceptions used across features
  policy/          Policy entity, enum, repository (no public write API)
  staff/           Staff entity, enum, repository, StaffAuth (header resolution)
  claim/           Claim + ClaimCommunication, status machine, service, controllers, DTOs, mapper
  workload/        WorkloadService, WorkloadController, response records
  exposure/        ExposureService, ExposureController, response records
  event/           ClaimEventPublisher port + LoggingClaimEventPublisher
```

Tests mirror production packages under `src/test/java`.

### Naming

- Java: typical Spring (`ClaimService`, `submitClaim`).
- HTTP: plural nouns, kebab-case path segments, camelCase JSON fields.
- DB: snake_case tables and columns (Spring Boot default physical naming).
- Claim business number: `CLM-{MARKET}-{8-digit seq}` e.g. `CLM-AU-00000001`.

---

## Success criteria

Binary. A criterion is met only when an automated test named in the blueprint asserts it. If a criterion has no test, it is not done.

### Claimant

- [ ] **SC-1** A claimant can report an incident by submitting a claim against an **active** policy (market + policy number). The API returns `201` with a unique `claimNumber`, status `OPEN`, and no assignee.
- [ ] **SC-2** A claimant can track a claim by `claimNumber` and see current status and incident summary (`200`).
- [ ] **SC-3** Tracking a known `claimNumber` that is `PENDING_INFORMATION` includes the latest staff information-request message.
- [ ] **SC-4** A claimant can provide additional information **only** when status is `PENDING_INFORMATION`; status then becomes `IN_PROGRESS` (`200`).
- [ ] **SC-5** After a claim is `SETTLED`, tracking returns status `SETTLED` and `settlementAmount`.
- [ ] **SC-6** After a claim is `REJECTED`, tracking returns status `REJECTED` and `rejectionReason`.
- [ ] **SC-7** Tracking an unknown `claimNumber` returns `404` `ProblemDetail`.
- [ ] **SC-8** Submitting against a missing policy returns `404` `ProblemDetail`.
- [ ] **SC-9** Submitting against a `LAPSED` or `CANCELLED` policy returns `422` `ProblemDetail`.
- [ ] **SC-10** Submit with invalid body (missing required field, `claimedAmount <= 0`, future `incidentDate`, malformed email) returns `400` `ProblemDetail`.
- [ ] **SC-11** Claimant track/info responses **do not** include `reserveAmount`, staff identity, or internal notes.

### Staff — intake and lifecycle

- [ ] **SC-12** Staff with header `X-Staff-Id` can list the incoming queue: `OPEN` claims in **their market**, unassigned, newest first.
- [ ] **SC-13** Staff can pick up an `OPEN` claim in their market: status becomes `IN_PROGRESS`, `assignedStaffId` is that staff member, `assignedAt` is set (`200`).
- [ ] **SC-14** Picking up a claim that is no longer `OPEN` (already assigned or terminal) returns `409` `ProblemDetail`. Concurrent double-assign: exactly one succeeds; the other `409`.
- [ ] **SC-15** Staff can retrieve full claim detail for a claim in their market (policy snapshot, amounts, communications, assignee) (`200`).
- [ ] **SC-16** Staff can update `reserveAmount` only while status is `IN_PROGRESS`; reserve must be `>= 0` and `<= policy.sumInsured`.
- [ ] **SC-17** Staff can request information only from `IN_PROGRESS`: status becomes `PENDING_INFORMATION`; a communication of kind `INFORMATION_REQUEST` is stored (`200`).
- [ ] **SC-18** Staff can settle only from `IN_PROGRESS`: status `SETTLED`, `settlementAmount > 0` and `<= policy.sumInsured`, `decidedAt` set, `rejectionReason` null (`200`).
- [ ] **SC-19** Staff can reject only from `IN_PROGRESS`: status `REJECTED`, non-blank `rejectionReason`, `settlementAmount` null, `decidedAt` set (`200`).
- [ ] **SC-20** Settle/reject/reserve/request-info on a claim not assigned to the calling officer returns `403` `ProblemDetail`. Managers **may** act on any in-market in-progress claim (override for assessment demo).
- [ ] **SC-21** Any write against `SETTLED` or `REJECTED` returns `409` `ProblemDetail`.
- [ ] **SC-22** Staff acting on a claim whose `market` ≠ staff `market` returns `403` `ProblemDetail`.
- [ ] **SC-23** Missing or unknown `X-Staff-Id` on `/api/v1/staff/**` returns `401` `ProblemDetail`. Claimant endpoints do not require the header.
- [ ] **SC-24** Providing information when status is not `PENDING_INFORMATION` returns `409` `ProblemDetail`.

### Workload, performance, exposure

- [ ] **SC-25** `GET /api/v1/staff/workload` for an officer returns incoming-queue count (their market), their non-terminal assigned claims, and team aggregates for their `team` (counts by status, per-officer open load).
- [ ] **SC-26** The same endpoint includes performance: counts of `SETTLED` and `REJECTED` with `decidedAt` in the last 30 days for that team, plus average hours from `assignedAt` to `decidedAt` for those decisions (empty team decisions → `averageHoursToDecision` null).
- [ ] **SC-27** A `MANAGER` sees the same shape scoped to **all teams in their market** (not a single team).
- [ ] **SC-28** `GET /api/v1/staff/exposure` returns outstanding liability: `sum(reserveAmount)` and claim count where status ∈ {`OPEN`,`IN_PROGRESS`,`PENDING_INFORMATION`}, broken down by market and by coverage type. Optional `?market=` filters to one market. Staff may only request their own market unless they are `MANAGER` (managers may omit filter for their market only — **ASSUMPTION:** this service is single-market-scoped per user; managers still belong to one market; exposure is that market. Cross-market rollup is out of scope).
- [ ] **SC-29** A `SETTLED` or `REJECTED` claim’s reserve is **excluded** from outstanding exposure.
- [ ] **SC-30** Newly submitted claim: `reserveAmount` defaults to `claimedAmount` and is included in exposure immediately.

### Platform

- [ ] **SC-31** `./mvnw verify` passes with Testcontainers (no local Postgres required for tests).
- [ ] **SC-32** Application starts against docker-compose Postgres with Flyway; OpenAPI UI is served.
- [ ] **SC-33** All error responses are RFC 7807 `ProblemDetail` (`type`, `title`, `status`, `detail`, `instance`). No Spring default `/error` HTML or unstructured `{message}` bodies from our handlers.
- [ ] **SC-34** Domain events are published on submit, assign, information request, information provided, and decision via `ClaimEventPublisher` (at least a logging implementation). REST is not used as an async bus.

---

## Domain analysis

### Entities

```
Policy 1 ───< Claim 1 ───< ClaimCommunication
Staff  1 ───< Claim (assignedStaff, optional)
Staff  1 ───< ClaimCommunication (staff author, optional)
```

No separate `Claimant` table. Claimant is a value on `Claim` (name, email, phone). **ASSUMPTION:** this assessment has no claimant identity provider.

No separate `Assignment` history table. Current assignee lives on `Claim`. **ASSUMPTION:** reassignment and assignment audit are out of scope.

### Policy

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| policyNumber | String | Unique **together with** market |
| market | `Market` | See enum |
| coverageType | `CoverageType` | `MOTOR` \| `PROPERTY` |
| holderName | String | Seeded; not a login |
| sumInsured | BigDecimal | `> 0` |
| status | `PolicyStatus` | `ACTIVE` \| `LAPSED` \| `CANCELLED` |
| createdAt / updatedAt | Instant | |

**ASSUMPTION:** there is no external policy system. A small set of policies is Flyway-seeded. Intake looks up `(policyNumber, market)` inside this DB.

**ASSUMPTION:** policy period, deductibles, endorsements, and “incident within cover period” are not modelled. Active vs not-active is the only eligibility check.

### Staff

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK; value sent as `X-Staff-Id` |
| fullName | String | |
| email | String | Unique |
| market | `Market` | Staff work one market |
| team | String | e.g. `AU-MOTOR-1` |
| role | `StaffRole` | `OFFICER` \| `MANAGER` |
| active | boolean | Inactive staff → `401` |
| createdAt | Instant | |

**ASSUMPTION:** no OAuth2/JWT/LDAP. Staff authentication is “known UUID in `X-Staff-Id` matching an active row”. Claimant APIs are unauthenticated knowledge-of-`claimNumber`.

### Claim

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| claimNumber | String | Unique business key, from sequence |
| policy | Policy | Many claims per policy **allowed** |
| market | Market | Copied from policy at submit; never diverges |
| coverageType | CoverageType | Copied from policy at submit |
| status | `ClaimStatus` | See state machine |
| claimantName / Email / Phone | String | |
| incidentDate | LocalDate | Must be `<=` today (UTC date) |
| incidentLocation | String | Used for both motor and property |
| incidentDescription | String | |
| claimedAmount | BigDecimal | `> 0`, `<= policy.sumInsured` |
| reserveAmount | BigDecimal | `>= 0`, `<= policy.sumInsured`; default `claimedAmount` |
| settlementAmount | BigDecimal | Null unless `SETTLED` |
| rejectionReason | String | Null unless `REJECTED` |
| assignedStaff | Staff | Null iff `OPEN` |
| assignedAt | Instant | Null iff `OPEN` |
| decidedAt | Instant | Null unless terminal |
| version | long | `@Version` optimistic lock |
| createdAt / updatedAt | Instant | |
| communications | List\<ClaimCommunication\> | Lazy |

**ASSUMPTION:** one location+description pair covers motor and property FNOL. No vehicle VIN / building ID.

**ASSUMPTION:** `claimedAmount` and `settlementAmount` must not exceed `sumInsured`. Settlement **may** differ from claimed (up or down) as long as it is `> 0` and `<= sumInsured`.

### ClaimCommunication

| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| claim | Claim | |
| kind | `INFORMATION_REQUEST` \| `INFORMATION_RESPONSE` \| `NOTE` | |
| body | String | Non-blank |
| authorType | `CLAIMANT` \| `STAFF` | |
| staff | Staff | Required iff author is staff |
| createdAt | Instant | |

Staff “review/assess” uses GET detail + optional `NOTE` is **not** required for v1. Assessment write path is **reserve update**. Information request/response are the only communications the API must create.

### Enums (closed sets — also CHECK constraints)

```
Market:        AU, HK, SG, JP, KR, TW
CoverageType:  MOTOR, PROPERTY
PolicyStatus:  ACTIVE, LAPSED, CANCELLED
StaffRole:     OFFICER, MANAGER
ClaimStatus:   OPEN, IN_PROGRESS, PENDING_INFORMATION, SETTLED, REJECTED
```

**ASSUMPTION:** “six markets” are Australia, Hong Kong, Singapore, Japan, Korea, Taiwan. The brief does not name them.

### State machine (invariants)

```
                    submit
                      │
                      ▼
                    OPEN ──────── assign ──────► IN_PROGRESS
                                                    │
                         ┌──────────────────────────┤
                         │ request info             │ settle / reject
                         ▼                          ▼
               PENDING_INFORMATION              SETTLED / REJECTED
                         │                         (terminal)
                         │ provide info
                         ▼
                   IN_PROGRESS
```

Legal transitions:

| From | Event | To | Actor |
|---|---|---|---|
| (none) | submit | OPEN | claimant |
| OPEN | assign | IN_PROGRESS | staff, same market |
| IN_PROGRESS | requestInformation | PENDING_INFORMATION | assignee or in-market manager |
| PENDING_INFORMATION | provideInformation | IN_PROGRESS | claimant |
| IN_PROGRESS | updateReserve | IN_PROGRESS | assignee or in-market manager |
| IN_PROGRESS | settle | SETTLED | assignee or in-market manager |
| IN_PROGRESS | reject | REJECTED | assignee or in-market manager |

Everything else is illegal → `IllegalClaimStateException` → `409`.

Additional invariants:

- `OPEN` ⇔ `assignedStaff == null` ⇔ `assignedAt == null`.
- Non-`OPEN` ⇒ assignee and `assignedAt` present.
- `SETTLED` ⇔ `settlementAmount != null` ⇔ `rejectionReason == null` ⇔ `decidedAt != null`.
- `REJECTED` ⇔ `rejectionReason` non-blank ⇔ `settlementAmount == null` ⇔ `decidedAt != null`.
- Non-terminal ⇒ `decidedAt`, `settlementAmount`, `rejectionReason` all null.
- Claim `market` / `coverageType` always equal the linked policy’s values (copied at insert; not updated).
- Multiple claims per policy are allowed.
- Duplicate submit is **not** idempotent: two valid submits create two claims. **ASSUMPTION:** no `Idempotency-Key` header in v1.

### Liability exposure

**Outstanding liability** = `SUM(reserve_amount)` WHERE `status IN ('OPEN','IN_PROGRESS','PENDING_INFORMATION')`.

Terminal claims contribute **zero**, even if `reserveAmount` column still holds the last reserve (do not null it out; filter by status). This keeps an audit of the last reserve without affecting exposure.

### Workload and performance

- **Incoming queue:** `OPEN` + unassigned + `market = staff.market`, ordered by `createdAt DESC`.
- **My work:** assigned to me and status not terminal.
- **Team workload (officer):** all non-terminal claims whose assignee’s `team` equals the caller’s team, plus unassigned `OPEN` in that market (queue is market-wide, not team-wide — **ASSUMPTION:** any in-market officer may pick up any in-market `OPEN` claim; team is a reporting dimension, not a queue partition).
- **Performance window:** rolling 30 × 24 hours from `Instant.now()`, based on `decidedAt`.

### Service boundary

**ASSUMPTION:** **one** deployable. A production Chubb landscape would split Policy, Identity, Notification, and Payments. One service is the right call for a 2–3 hour slice: one transaction covers “create claim + default reserve + event”, and we do not pay distributed-consistency tax. Package-level features (`policy`, `claim`, `staff`, `workload`, `exposure`) are the internal boundaries.

### Sync vs async

| Concern | Channel | Why |
|---|---|---|
| Submit, track, assign, decide, info, workload, exposure | REST, synchronous | Caller is waiting; needs 2xx/4xx now |
| Downstream notify claimant, refresh manager dashboards, warehouse | Domain events via `ClaimEventPublisher` | Side effects; at-least-once later |

**ASSUMPTION:** do **not** run Kafka in this timebox. Define a port:

```java
public interface ClaimEventPublisher {
  void publish(ClaimEvent event);
}
```

`ClaimEvent` is a sealed type or enum+payload: `CLAIM_SUBMITTED`, `CLAIM_ASSIGNED`, `INFORMATION_REQUESTED`, `INFORMATION_PROVIDED`, `CLAIM_DECIDED`. Default bean: `LoggingClaimEventPublisher`. Topic names for the walkthrough (not implemented): `chubb.claims.claim-submitted.v1`, `…assigned.v1`, `…information-requested.v1`, `…information-provided.v1`, `…decided.v1`. Keys: `claimNumber`.

REST is wrong for those notifications (staff decide should not block on email). Kafka is wrong for “GET my claim status”.

### Read/write profile

- **Writes:** low volume, strong consistency, state machine, optimistic lock on assign/decide.
- **Queue / workload / exposure:** read-mostly. v1 = SQL aggregates on `claim` (indexes below). No CQRS/read-model table yet. Call this out as the next scaling step.

---

## Implementation blueprint

Do not start milestone *N+1* until milestone *N*’s **gate command** exits 0.

---

### M0 — Project skeleton

**Intent:** empty but real Spring Boot app; CI-runnable `./mvnw verify`; local Postgres via Compose; Testcontainers base class ready.

#### Tasks (ordered)

1. **CREATE** `.gitignore` — Maven, IDE, `.env`, `target/`, `.DS_Store`.
2. **CREATE** `pom.xml`
   - Parent: `org.springframework.boot:spring-boot-starter-parent:3.5.5` (or latest 3.5.x if 3.5.5 is missing — do not jump to 4.x).
   - `java.version`: 21.
   - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `lombok` (optional, provided), `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` (compatible 2.8.x).
   - Test: `spring-boot-starter-test` (excludes vintage), `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`.
   - AssertJ/Mockito/JUnit 5 come from `starter-test`. Do not add extra assertion libraries.
   - Compiler `-parameters`. Surefire + Failsafe both bound; Failsafe runs `*IT`.
3. **CREATE** Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/*`) so `./mvnw` works with no global Maven.
4. **CREATE** `src/main/java/com/chubb/claims/ClaimsServiceApplication.java` — `@SpringBootApplication`, `main`.
5. **CREATE** `src/main/resources/application.yml`

```yaml
spring:
  application.name: claims-service
  datasource:
    url: jdbc:postgresql://localhost:5432/claims
    username: claims
    password: claims
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
    properties.hibernate.jdbc.time_zone: UTC
  flyway.enabled: true
server.port: 8080
```

   No `ddl-auto: update`. No `open-in-view: true`.

6. **CREATE** `docker-compose.yml` — service `postgres:16-alpine`, db/user/password `claims`, port `5432`, volume, healthcheck `pg_isready`.
7. **CREATE** `src/test/java/com/chubb/claims/AbstractPostgresIT.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
```

   Use this only from M5. M1–M2 may use slimmer slices.

8. **CREATE** `src/test/resources/application-test.yml` — `spring.jpa.open-in-view: false`, `ddl-auto: validate`, Flyway on. **No** hardcoded JDBC URL (ServiceConnection supplies it).
9. **CREATE** `src/test/java/com/chubb/claims/ClaimsServiceApplicationTest.java` — `@SpringBootTest` + Testcontainers (extend `AbstractPostgresIT` **or** duplicate the container if Flyway has no migrations yet). `contextLoads()`. **If Flyway fails on empty `db/migration`:** add **CREATE** `src/main/resources/db/migration/V0__baseline.sql` containing only `-- baseline` (a comment is enough for Flyway to succeed). Prefer this over disabling Flyway in tests.
10. **CREATE** `.github/workflows/ci.yml` — JDK 21 Temurin, checkout, `./mvnw -B verify`. Runners need Docker (Testcontainers).
11. **CREATE** `README.md` — how to `docker compose up -d`, `./mvnw spring-boot:run`, Swagger URL, `./mvnw verify`. Keep short.
12. **CREATE** `AI_JOURNAL.md` — running log of AI prompts, accepts, challenges, overrides. Update every milestone.

#### Tests (M0)

- `ClaimsServiceApplicationTest.contextLoads` — Spring context starts against Testcontainers Postgres.

#### Gate

```bash
./mvnw verify
```

Do not proceed until this is green.

---

### M1 — Database schema

**Intent:** Flyway encodes **all** invariants as UNIQUE / FK / CHECK / indexes. Hibernate is not the source of truth.

#### Tasks (ordered)

1. **CREATE** `src/main/resources/db/migration/V1__create_policy.sql`

```sql
CREATE TABLE policy (
    id              UUID PRIMARY KEY,
    policy_number   VARCHAR(32)  NOT NULL,
    market          VARCHAR(8)   NOT NULL,
    coverage_type   VARCHAR(16)  NOT NULL,
    holder_name     VARCHAR(255) NOT NULL,
    sum_insured     NUMERIC(19,2) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_policy_number_market UNIQUE (policy_number, market),
    CONSTRAINT chk_policy_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_policy_coverage CHECK (coverage_type IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT chk_policy_status CHECK (status IN ('ACTIVE', 'LAPSED', 'CANCELLED')),
    CONSTRAINT chk_policy_sum_insured CHECK (sum_insured > 0)
);
```

2. **CREATE** `src/main/resources/db/migration/V2__create_staff.sql`

```sql
CREATE TABLE staff (
    id          UUID PRIMARY KEY,
    full_name   VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    market      VARCHAR(8)   NOT NULL,
    team        VARCHAR(64)  NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_staff_email UNIQUE (email),
    CONSTRAINT chk_staff_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_staff_role CHECK (role IN ('OFFICER', 'MANAGER'))
);
```

3. **CREATE** `src/main/resources/db/migration/V3__create_claim.sql`

```sql
CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE claim (
    id                   UUID PRIMARY KEY,
    claim_number         VARCHAR(32)   NOT NULL,
    policy_id            UUID          NOT NULL REFERENCES policy (id),
    market               VARCHAR(8)    NOT NULL,
    coverage_type        VARCHAR(16)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    claimant_name        VARCHAR(255)  NOT NULL,
    claimant_email       VARCHAR(255)  NOT NULL,
    claimant_phone       VARCHAR(32)   NOT NULL,
    incident_date        DATE          NOT NULL,
    incident_location    VARCHAR(512)  NOT NULL,
    incident_description TEXT          NOT NULL,
    claimed_amount       NUMERIC(19,2) NOT NULL,
    reserve_amount       NUMERIC(19,2) NOT NULL,
    settlement_amount    NUMERIC(19,2),
    rejection_reason     TEXT,
    assigned_staff_id    UUID          REFERENCES staff (id),
    assigned_at          TIMESTAMPTZ,
    decided_at           TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_claim_number UNIQUE (claim_number),
    CONSTRAINT chk_claim_market CHECK (market IN ('AU', 'HK', 'SG', 'JP', 'KR', 'TW')),
    CONSTRAINT chk_claim_coverage CHECK (coverage_type IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT chk_claim_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'PENDING_INFORMATION', 'SETTLED', 'REJECTED')),
    CONSTRAINT chk_claimed_amount CHECK (claimed_amount > 0),
    CONSTRAINT chk_reserve_amount CHECK (reserve_amount >= 0),
    CONSTRAINT chk_settlement_positive CHECK (settlement_amount IS NULL OR settlement_amount > 0),
    CONSTRAINT chk_open_unassigned CHECK (
        (status = 'OPEN' AND assigned_staff_id IS NULL AND assigned_at IS NULL)
        OR status <> 'OPEN'),
    CONSTRAINT chk_non_open_assigned CHECK (
        status = 'OPEN'
        OR (assigned_staff_id IS NOT NULL AND assigned_at IS NOT NULL)),
    CONSTRAINT chk_settled CHECK (
        (status = 'SETTLED'
            AND settlement_amount IS NOT NULL
            AND decided_at IS NOT NULL
            AND rejection_reason IS NULL)
        OR status <> 'SETTLED'),
    CONSTRAINT chk_rejected CHECK (
        (status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND length(trim(rejection_reason)) > 0
            AND decided_at IS NOT NULL
            AND settlement_amount IS NULL)
        OR status <> 'REJECTED'),
    CONSTRAINT chk_undecided CHECK (
        status IN ('SETTLED', 'REJECTED')
        OR (decided_at IS NULL AND settlement_amount IS NULL AND rejection_reason IS NULL))
);

CREATE INDEX idx_claim_queue
    ON claim (market, created_at DESC)
    WHERE status = 'OPEN' AND assigned_staff_id IS NULL;

CREATE INDEX idx_claim_assignee_status ON claim (assigned_staff_id, status);
CREATE INDEX idx_claim_exposure ON claim (market, coverage_type, status);
```

   If V0 baseline exists, numbering is V1… as above (V0 then V1). Do not skip numbers.

4. **CREATE** `src/main/resources/db/migration/V4__create_claim_communication.sql`

```sql
CREATE TABLE claim_communication (
    id           UUID PRIMARY KEY,
    claim_id     UUID         NOT NULL REFERENCES claim (id),
    kind         VARCHAR(32)  NOT NULL,
    body         TEXT         NOT NULL,
    author_type  VARCHAR(16)  NOT NULL,
    staff_id     UUID         REFERENCES staff (id),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_comm_kind CHECK (kind IN (
        'INFORMATION_REQUEST', 'INFORMATION_RESPONSE', 'NOTE')),
    CONSTRAINT chk_comm_author CHECK (author_type IN ('CLAIMANT', 'STAFF')),
    CONSTRAINT chk_comm_staff CHECK (
        (author_type = 'STAFF' AND staff_id IS NOT NULL)
        OR (author_type = 'CLAIMANT' AND staff_id IS NULL)),
    CONSTRAINT chk_comm_body CHECK (length(trim(body)) > 0)
);

CREATE INDEX idx_comm_claim_created ON claim_communication (claim_id, created_at);
```

5. **CREATE** `src/main/resources/db/migration/V5__seed_reference_data.sql`  
   Insert **fixed UUIDs** (literal constants) so tests and README can use them:
   - Policies: at least one `ACTIVE` MOTOR and one `ACTIVE` PROPERTY in `AU`; one `LAPSED` in `AU`; one `ACTIVE` in `HK` (proves market isolation). `sum_insured` e.g. `50000.00` / `250000.00`.
   - Staff (all `active = true`):
     - `OFFICER` AU team `AU-MOTOR-1` — id **`11111111-1111-1111-1111-111111111111`**
     - `OFFICER` AU team `AU-MOTOR-1` — id **`22222222-2222-2222-2222-222222222222`** (concurrent assign tests)
     - `MANAGER` AU team `AU-MGMT` — id **`33333333-3333-3333-3333-333333333333`**
     - `OFFICER` HK — id **`44444444-4444-4444-4444-444444444444`**
   - Policy numbers: `POL-AU-MOTOR-001`, `POL-AU-PROP-001`, `POL-AU-MOTOR-LAPSED`, `POL-HK-MOTOR-001`.

6. **CREATE** `src/test/java/com/chubb/claims/FlywayMigrationIT.java` (Failsafe `*IT`)
   - `@Testcontainers` + `PostgreSQLContainer`.
   - **No** `@SpringBootTest` required: `Flyway.configure().dataSource(container.getJdbcUrl(), user, password).load().migrate()`.
   - Assert `migrate()` succeeds (`migrationsExecuted >= 1` after V0/V1…).
   - Optional: query `information_schema.tables` for `policy`, `staff`, `claim`, `claim_communication`.

#### Tests (M1)

- `FlywayMigrationIT.migratesSuccessfully`

#### Gate

```bash
./mvnw verify -Dtest=FlywayMigrationIT -Dit.test=FlywayMigrationIT
```

If Failsafe is used, `./mvnw verify` is enough as long as `FlywayMigrationIT` runs. Prefer:

```bash
./mvnw verify
```

still green (includes M0). Do not proceed on red.

---

### M2 — Entities + repositories

**Intent:** JPA model is a 1:1 mirror of M1. Slice tests prove round-trips and that DB constraints fire.

#### Tasks (ordered)

1. **CREATE** enums (STRING persisted):
   - `com.chubb.claims.shared.domain.Market`
   - `com.chubb.claims.shared.domain.CoverageType`
   - `com.chubb.claims.policy.PolicyStatus`
   - `com.chubb.claims.staff.StaffRole`
   - `com.chubb.claims.claim.ClaimStatus`
   - `com.chubb.claims.claim.CommunicationKind`
   - `com.chubb.claims.claim.AuthorType`

2. **CREATE** `com.chubb.claims.policy.Policy`
   - `@Entity @Table(name = "policy")`
   - `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`
   - `@Enumerated(EnumType.STRING)` on enums
   - `@ManyToOne` — none
   - equals/hashCode on `id` only
   - `@PrePersist` / `@PreUpdate` for timestamps **or** explicit sets in factory methods

3. **CREATE** `com.chubb.claims.policy.PolicyRepository` extends `JpaRepository<Policy, UUID>`
   - `Optional<Policy> findByPolicyNumberAndMarket(String policyNumber, Market market);`

4. **CREATE** `com.chubb.claims.staff.Staff` + `StaffRepository`
   - `Optional<Staff> findByIdAndActiveTrue(UUID id);`
   - `List<Staff> findByMarketAndTeam(Market market, String team);`

5. **CREATE** `com.chubb.claims.claim.Claim`
   - `@ManyToOne(fetch = LAZY, optional = false) Policy policy`
   - `@ManyToOne(fetch = LAZY) Staff assignedStaff`
   - `@OneToMany(mappedBy = "claim", fetch = LAZY, cascade = PERSIST) List<ClaimCommunication> communications` (initialize to `new ArrayList<>()`)
   - `@Version long version`
   - **No** `FetchType.EAGER` anywhere
   - Factory `Claim.open(...)` setting `OPEN`, null assignee, reserve = claimed, copied market/coverage

6. **CREATE** `com.chubb.claims.claim.ClaimCommunication` — `@ManyToOne(fetch = LAZY)` to `Claim` and `Staff`

7. **CREATE** `com.chubb.claims.claim.ClaimRepository`
   - `Optional<Claim> findByClaimNumber(String claimNumber);`
   - `List<Claim> findByMarketAndStatusAndAssignedStaffIsNullOrderByCreatedAtDesc(Market market, ClaimStatus status);`
   - Custom `@Query` for exposure aggregate (used in M3; can be added in M3 if preferred — if added here, test it here):

```java
@Query("""
    select c.market as market, c.coverageType as coverageType,
           coalesce(sum(c.reserveAmount), 0) as totalReserve,
           count(c) as claimCount
    from Claim c
    where c.status in :statuses
      and (:market is null or c.market = :market)
    group by c.market, c.coverageType
    """)
List<ExposureProjection> aggregateExposure(Collection<ClaimStatus> statuses, Market market);
```

   Projection interface in `exposure` package is fine in M3; a package-private interface in `claim` for M2 is acceptable.

8. **CREATE** `com.chubb.claims.claim.ClaimCommunicationRepository`

9. **CREATE** `com.chubb.claims.claim.ClaimNumberSequence` + `PostgresClaimNumberSequence`
   - `long nextValue()` → native `SELECT nextval('claim_number_seq')`
   - `com.chubb.claims.claim.ClaimNumbers.format(Market, long)` → `CLM-%s-%08d`

10. **CREATE** test support: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Testcontainers` + `@ServiceConnection` + `@ActiveProfiles("test")` abstract `AbstractJpaTest`.

    Import Flyway auto-config (default with `@DataJpaTest` in Boot 3 may **exclude** Flyway). **Required:** `@AutoConfigureTestDatabase(replace = NONE)` and ensure Flyway runs. If schema missing, add `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` or `@SpringBootTest` only if the slice cannot apply migrations — prefer getting `@DataJpaTest` + Flyway working rather than jumping to full context.

#### Tests (M2) — `src/test/java/...`

| Class | What it proves |
|---|---|
| `PolicyRepositoryTest` | Save/load ACTIVE policy; unique `(policy_number, market)` → `DataIntegrityViolationException` |
| `StaffRepositoryTest` | Round-trip; unique email violation |
| `ClaimRepositoryTest` | Persist OPEN claim with UUID id and `CLM-AU-00000001`-style number; findByClaimNumber; communications cascade persist |
| `ClaimConstraintTest` | Insert `OPEN` **with** `assigned_staff_id` set → constraint violation; `SETTLED` without `settlement_amount` → violation; `claimed_amount = 0` → violation |
| `ClaimNumberSequenceTest` | Two `nextValue()` calls are strictly increasing |

Use AssertJ `assertThatThrownBy`. Do not catch-and-ignore.

#### Gate

```bash
./mvnw test -Dtest=*RepositoryTest,*ConstraintTest,*SequenceTest
./mvnw verify
```

---

### M3 — Domain services

**Intent:** every business rule lives in services with explicit `@Transactional` boundaries. Constraint violations and illegal transitions become domain exceptions. **No Spring context** in these tests — plain unit tests, Mockito for repositories/publisher/clock.

#### Domain exceptions — CREATE `com.chubb.claims.shared.error`

```java
public abstract class DomainException extends RuntimeException {
  private final String typeSuffix; // e.g. "claim-not-found"
  private final HttpStatus status;
  // ctor(String typeSuffix, HttpStatus status, String message)
}
public class ClaimNotFoundException extends DomainException { /* 404 */ }
public class PolicyNotFoundException extends DomainException { /* 404 */ }
public class PolicyNotActiveException extends DomainException { /* 422 */ }
public class IllegalClaimStateException extends DomainException { /* 409 */ }
public class ClaimAlreadyAssignedException extends DomainException { /* 409 */ }
public class StaffUnauthorizedException extends DomainException { /* 401 */ }
public class StaffForbiddenException extends DomainException { /* 403 */ }
public class ReserveLimitException extends DomainException { /* 422 */ }
```

Map `typeSuffix` to `urn:chubb:claims:problem:{typeSuffix}` in M4.

#### Clock

**CREATE** inject `java.time.Clock` bean (`Clock.systemUTC()` in `config`). Services use `clock.instant()` and `LocalDate.now(clock)`. Tests pass `Clock.fixed`.

#### Event types — CREATE `com.chubb.claims.event`

```java
public enum ClaimEventType {
  CLAIM_SUBMITTED, CLAIM_ASSIGNED, INFORMATION_REQUESTED,
  INFORMATION_PROVIDED, CLAIM_DECIDED
}
public record ClaimEvent(ClaimEventType type, String claimNumber, Instant occurredAt, Map<String, Object> payload) {}
public interface ClaimEventPublisher { void publish(ClaimEvent event); }
public class LoggingClaimEventPublisher implements ClaimEventPublisher { /* slf4j */ }
```

**CREATE** `@Configuration` `@Bean` of the logging publisher (used from M5; M3 tests mock the interface).

#### ClaimService — CREATE `com.chubb.claims.claim.ClaimService`

Signatures (command objects as package records in `claim.dto` **or** inner to service — keep them **out** of the web package so M3 has no servlet types):

```java
@Validated
@Service
public class ClaimService {
  public Claim submit(SubmitClaimCommand cmd);                    // @Transactional
  public Claim getByClaimNumber(String claimNumber);              // readOnly; throws not found
  public Claim provideInformation(String claimNumber, String body); // @Transactional
  public Claim assignToSelf(String claimNumber, UUID staffId);    // @Transactional
  public Claim requestInformation(String claimNumber, UUID staffId, String body);
  public Claim updateReserve(String claimNumber, UUID staffId, BigDecimal reserve);
  public Claim settle(String claimNumber, UUID staffId, BigDecimal settlementAmount);
  public Claim reject(String claimNumber, UUID staffId, String reason);
  public List<Claim> incomingQueue(UUID staffId);                 // readOnly
  public Claim getForStaff(String claimNumber, UUID staffId);     // readOnly + market check
}
```

`SubmitClaimCommand` fields: `policyNumber`, `market`, `claimantName`, `claimantEmail`, `claimantPhone`, `incidentDate`, `incidentLocation`, `incidentDescription`, `claimedAmount`.

Behaviour:

- `submit`: load policy; if missing → `PolicyNotFoundException`; if not `ACTIVE` → `PolicyNotActiveException`; if `incidentDate > LocalDate.now(clock)` → `IllegalArgumentException` (M4 maps to 400) **or** a `DomainException` 400; if `claimedAmount > sumInsured` → `ReserveLimitException` (422). Generate number, `Claim.open`, save, publish `CLAIM_SUBMITTED`.
- `assignToSelf`: load staff (active); load claim; market match; status must be `OPEN` else `ClaimAlreadyAssignedException` or `IllegalClaimStateException`; set assignee/status/`assignedAt`; `saveAndFlush`; on `OptimisticLockException` / 0-row → `ClaimAlreadyAssignedException`. Publish `CLAIM_ASSIGNED`.
  - Prefer **atomic** repository method in addition to `@Version`:

```java
@Modifying
@Query("update Claim c set c.assignedStaff = :staff, c.status = :inProgress, c.assignedAt = :at, c.version = c.version + 1, c.updatedAt = :at where c.claimNumber = :num and c.status = :open and c.assignedStaff is null")
int assignIfOpen(...);
```

  Use this **or** entity mutate + version, but the integration test in M5 must prove double-assign. If using JPQL update, reload entity after success.
- `requestInformation` / `updateReserve` / `settle` / `reject`: `assertActor(claim, staff)` — officer must be assignee; manager must share market. Then transition methods **on the aggregate** (`claim.requestInformation(...)`) that throw `IllegalClaimStateException` if status is wrong. Persist communication on request. Publish matching event.
- `provideInformation`: no staff header; status must be `PENDING_INFORMATION`; append `INFORMATION_RESPONSE`; back to `IN_PROGRESS`; publish.
- `incomingQueue`: staff’s market, `OPEN`, unassigned, `createdAt DESC`.

Put transition methods on `Claim` (aggregate), not in the controller. Service orchestrates persistence, authz, events.

#### WorkloadService — CREATE `com.chubb.claims.workload.WorkloadService`

```java
@Transactional(readOnly = true)
public WorkloadSnapshot get(UUID staffId);
```

`WorkloadSnapshot` is a **domain/service DTO** (not an entity): queueCount, myClaims, teamByOfficer, countsByStatus, performance `{settledCount, rejectedCount, averageHoursToDecision}`.

Officer: team = caller’s team (assignees in that team) + market queue count.  
Manager: all staff in caller’s **market**; queue count for that market.

Use repository queries; do not load the entire `claim` table into memory.

Need extra repository methods (add in this milestone, with a focused `@DataJpaTest` **or** cover via unit tests with mocked repo — unit tests mock repos; add `ClaimRepository` query methods here and a **thin** `ClaimRepositoryQueryTest` `@DataJpaTest` for the JPQL so M5 is not the first time the query runs):

- counts by status for market / team
- decided since `Instant cutoff` for performance
- average duration: compute in Java from a small list of `(assignedAt, decidedAt)` **or** JPQL `avg(extract epoch)` — **ASSUMPTION:** load decided claims in the window for the scope (team or market) and average in the service. Fine at this volume.

#### ExposureService — CREATE `com.chubb.claims.exposure.ExposureService`

```java
@Transactional(readOnly = true)
public ExposureSnapshot get(UUID staffId, Optional<Market> marketFilter);
```

Staff who pass a different market than their own → `StaffForbiddenException`. Null filter → caller’s market. Sum/count via `aggregateExposure` for statuses `OPEN`, `IN_PROGRESS`, `PENDING_INFORMATION`.

#### Tests (M3) — Mockito, **no** `@SpringBootTest` / `@ExtendWith(SpringExtension.class)` unless required for Mockito JUnit Jupiter (`@ExtendWith(MockitoExtension.class)` is required and is **not** a Spring context).

| Class | Rules |
|---|---|
| `ClaimServiceSubmitTest` | Active policy → OPEN, reserve=claimed, number format, event published; missing policy; lapsed policy; claimed > sumInsured; future incidentDate |
| `ClaimServiceAssignTest` | OPEN → IN_PROGRESS; already IN_PROGRESS → already-assigned; market mismatch → forbidden; inactive staff → unauthorized |
| `ClaimServiceInformationTest` | request only from IN_PROGRESS; provide only from PENDING_INFORMATION; wrong state → illegal state; event types |
| `ClaimServiceDecisionTest` | settle/reject from IN_PROGRESS; settle amount > sumInsured → 422-type; reject blank reason; terminal then settle → illegal; officer not assignee → forbidden; manager same market allowed |
| `ClaimServiceReserveTest` | IN_PROGRESS ok; OPEN not allowed; negative rejected (service-level); above sumInsured |
| `WorkloadServiceTest` | Officer sees own team performance window using fixed clock; manager sees market |
| `ExposureServiceTest` | OPEN reserve counted; SETTLED excluded; other-market filter forbidden |

Cover **SC-1,4,8,9,13,14,16–21,24,28–30** at unit level (HTTP codes are M4; exception types here).

#### Gate

```bash
./mvnw test -Dtest=*Service*Test
```

Then `./mvnw verify` still green.

---

### M4 — REST controllers

**Intent:** HTTP adapter only. Jakarta validation, OpenAPI annotations, `ProblemDetail` mapping. Slice tests: `@WebMvcTest`, **mock services**, never repositories.

#### Tasks (ordered)

1. **CREATE** `com.chubb.claims.shared.api.ProblemTypes` — `urn:chubb:claims:problem:{suffix}` constants.

2. **CREATE** `com.chubb.claims.shared.api.GlobalExceptionHandler` — **the only** `@RestControllerAdvice`.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(DomainException.class)           // uses ex.getStatus()
  @ExceptionHandler(MethodArgumentNotValidException.class) // 400, join field errors into detail
  @ExceptionHandler(HandlerMethodValidationException.class) // 400 (Boot 3.2+ method validation)
  @ExceptionHandler(ConstraintViolationException.class)     // 400
  @ExceptionHandler(MissingRequestHeaderException.class)    // 400 or 401 if X-Staff-Id — use 401
  @ExceptionHandler(DataIntegrityViolationException.class)  // 409
  @ExceptionHandler(OptimisticLockingFailureException.class)// 409 already-assigned
  @ExceptionHandler(Exception.class)                        // 500, generic detail, log stack
}
```

Each handler returns `ProblemDetail.forStatusAndDetail(...)` with `setType(URI.create(urn))`, `setTitle`, `setInstance(URI.create(request.getRequestURI()))`. Do not leak SQL / stack traces in `detail`.

3. **CREATE** web DTOs as records in `com.chubb.claims.claim.api` (and workload/exposure api packages):

```java
public record SubmitClaimRequest(
    @NotBlank String policyNumber,
    @NotNull Market market,
    @NotBlank String claimantName,
    @Email @NotBlank String claimantEmail,
    @NotBlank String claimantPhone,
    @NotNull @PastOrPresent LocalDate incidentDate,
    @NotBlank String incidentLocation,
    @NotBlank String incidentDescription,
    @NotNull @Positive BigDecimal claimedAmount
) {}

public record ClaimantClaimResponse(
    String claimNumber, ClaimStatus status, Market market, CoverageType coverageType,
    String claimantName, LocalDate incidentDate, String incidentLocation,
    String incidentDescription, BigDecimal claimedAmount,
    BigDecimal settlementAmount, String rejectionReason,
    String latestInformationRequest, Instant createdAt, Instant updatedAt
) {}

public record StaffClaimResponse( /* Claimant fields + */
    UUID id, String policyNumber, BigDecimal sumInsured, BigDecimal reserveAmount,
    UUID assignedStaffId, Instant assignedAt, Instant decidedAt,
    List<CommunicationResponse> communications
) {}

public record ProvideInformationRequest(@NotBlank String body) {}
public record RequestInformationRequest(@NotBlank String body) {}
public record UpdateReserveRequest(@NotNull @DecimalMin("0.00") BigDecimal reserveAmount) {}
public record DecisionRequest(
    @NotNull DecisionType type,  // SETTLED | REJECTED
    BigDecimal settlementAmount, // required when SETTLED — validate in @AssertTrue on the record
    String rejectionReason
) {
  @AssertTrue(message = "settlementAmount required when SETTLED")
  public boolean isSettlementValid() { /* ... */ }
  @AssertTrue(message = "rejectionReason required when REJECTED")
  public boolean isRejectionValid() { /* ... */ }
}
public record CommunicationResponse(CommunicationKind kind, AuthorType authorType, String body, Instant createdAt) {}
```

**CREATE** `ClaimMapper` (plain `@Component`, not MapStruct) converting `Claim` → claimant/staff responses. Claimant mapping **must omit** reserve and communications except `latestInformationRequest` (last `INFORMATION_REQUEST` body, else null).

4. **CREATE** `com.chubb.claims.claim.api.ClaimantClaimController` `@RequestMapping("/api/v1/claims")`

| Method | Path | Status | Notes |
|---|---|---|---|
| POST | `/` | 201 | `Location: /api/v1/claims/{claimNumber}` |
| GET | `/{claimNumber}` | 200 | Claimant DTO |
| POST | `/{claimNumber}/information` | 200 | Claimant DTO |

`@Operation` / `@ApiResponse` on each method. `@Valid @RequestBody`.

5. **CREATE** `com.chubb.claims.claim.api.StaffClaimController` `@RequestMapping("/api/v1/staff/claims")`

| Method | Path | Status |
|---|---|---|
| GET | `/queue` | 200 list of staff summaries (claimNumber, status, claimedAmount, createdAt) |
| GET | `/{claimNumber}` | 200 StaffClaimResponse |
| POST | `/{claimNumber}/assignment` | 200 |
| POST | `/{claimNumber}/information-requests` | 200 |
| PATCH | `/{claimNumber}/reserve` | 200 |
| POST | `/{claimNumber}/decision` | 200 |

Every method: `@RequestHeader("X-Staff-Id") UUID staffId`.

6. **CREATE** `com.chubb.claims.workload.api.WorkloadController` — `GET /api/v1/staff/workload` + response records matching SC-25/26/27.

7. **CREATE** `com.chubb.claims.exposure.api.ExposureController` — `GET /api/v1/staff/exposure?market=` optional.

8. **CREATE** `com.chubb.claims.config.OpenApiConfig` — API title “Chubb Claims Service”, version `0.1.0`, description of `X-Staff-Id`.

9. Pagination: **ASSUMPTION:** queue and lists are unbounded in v1 except incoming queue is naturally small. If more than 100 OPEN claims, still return all for the assessment. Do **not** add Spring `Pageable` unless a list would otherwise be unbounded in tests — skip pagination.

#### Tests (M4) — `@WebMvcTest(…Controller.class)` + `@Import(GlobalExceptionHandler.class)` + `@MockitoBean` / `@MockBean` for services + mapper if the controller uses a real mapper (prefer `@Import(ClaimMapper.class)` as it is stateless).

Use `MockMvc` + AssertJ json-path **or** `jsonPath`. Assert `Content-Type` contains `application/problem+json` on errors.

| Class | Cases |
|---|---|
| `ClaimantClaimControllerTest` | POST 201 + Location; POST 400 invalid body ProblemDetail shape (`type`, `title`, `status`, `detail`, `instance`); GET 200 claimant JSON has no `reserveAmount`; GET service throws not found → 404; POST information |
| `StaffClaimControllerTest` | missing header → 401; queue 200; assign 200; assign domain already-assigned → 409; decision 400 when SETTLED without amount; market forbidden → 403 |
| `GlobalExceptionHandlerTest` | can live as tests above; add one 500 fallback test with `given(service...).willThrow(new RuntimeException("secret"))` and assert detail does **not** contain `"secret"` |
| `WorkloadControllerTest` | 200 JSON structure |
| `ExposureControllerTest` | 200 JSON structure |

Controllers return DTOs only — a test that JSON has `hibernateLazyInitializer` fails the milestone.

#### Gate

```bash
./mvnw test -Dtest=*ControllerTest
./mvnw verify
```

---

### M5 — End-to-end

**Intent:** one `@SpringBootTest` + Testcontainers test per user journey in the brief. Real HTTP (`TestRestTemplate` or `MockMvc` with full context), real DB, **real repositories — never mocked**. Gate is the full suite.

#### Tasks (ordered)

1. **MODIFY** if needed: ensure `LoggingClaimEventPublisher` is the only publisher bean.
2. **CREATE** `src/test/java/com/chubb/claims/journey/ClaimantSubmitAndTrackIT.java`
3. **CREATE** `src/test/java/com/chubb/claims/journey/StaffPickupAssessSettleIT.java`
4. **CREATE** `src/test/java/com/chubb/claims/journey/InformationRequestRoundTripIT.java`
5. **CREATE** `src/test/java/com/chubb/claims/journey/StaffRejectIT.java`
6. **CREATE** `src/test/java/com/chubb/claims/journey/ConcurrentAssignIT.java`
7. **CREATE** `src/test/java/com/chubb/claims/journey/WorkloadAndExposureIT.java`
8. **CREATE** `src/test/java/com/chubb/claims/journey/PolicyEligibilityIT.java`
9. **CREATE** `src/test/java/com/chubb/claims/journey/ErrorContractIT.java`
10. Extend `AbstractPostgresIT`. Use seeded UUIDs and policy numbers from V5. `TestRestTemplate` + `@LocalServerPort` **or** `MockMvc` with `@AutoConfigureMockMvc` — either is full Spring; HTTP-level `TestRestTemplate` better matches “user journey”.

Each IT class = one journey. Shared helpers for `submitAuMotorClaim()` live in `journey/JourneySupport` — keep them test-only.

#### Journeys (map to success criteria)

| Test class | Flow | Asserts |
|---|---|---|
| `ClaimantSubmitAndTrackIT` | POST claim AU active motor → GET by claimNumber | 201, OPEN, no assignee; track 200; JSON has no reserveAmount (**SC-1,2,11**) |
| `StaffPickupAssessSettleIT` | submit → officer 1111… GET queue contains it → POST assignment → PATCH reserve → POST decision SETTLED | queue empty after assign; track shows SETTLED + amount (**SC-5,12,13,15,16,18**) |
| `InformationRequestRoundTripIT` | assign → request info → claimant GET shows latest request → claimant POST information → staff settle | statuses PENDING_INFORMATION → IN_PROGRESS → SETTLED (**SC-3,4,17**) |
| `StaffRejectIT` | assign → REJECTED with reason | claimant track has reason, no settlementAmount (**SC-6,19**) |
| `ConcurrentAssignIT` | submit → two threads POST assignment as 1111… and 2222… | one 200, one 409; exactly one assignee in DB (**SC-14**) |
| `WorkloadAndExposureIT` | two OPEN claims + one settled | exposure sum equals remaining reserves; settled excluded; officer workload queueCount and performance (**SC-25,26,28,29,30**) |
| `PolicyEligibilityIT` | lapsed policy 422; unknown policy 404; HK officer 4444… assign on AU claim 403 (**SC-8,9,22**) |
| `ErrorContractIT` | GET unknown claim; POST info on OPEN; POST decision on OPEN; missing X-Staff-Id | 404/409/409/401; body has `type` starting `urn:chubb:claims:problem:` (**SC-7,21,23,24,33**) |

Also assert OpenAPI: `GET /v3/api-docs` is `200` in `ErrorContractIT` or a tiny `OpenApiIT` (**SC-32** docs portion).

#### Gate

```bash
./mvnw verify
```

This is the release gate. All of M0–M5 tests run. Do not merge or demo on a red verify.

---

## Validation gates (summary)

| Milestone | Command | Proceed only if |
|---|---|---|
| M0 | `./mvnw verify` | Context loads on empty/baseline schema |
| M1 | `./mvnw verify` | Flyway applies on Testcontainers |
| M2 | `./mvnw test -Dtest=*RepositoryTest,*ConstraintTest,*SequenceTest` then `./mvnw verify` | Round-trips + CHECK/UNIQUE proven |
| M3 | `./mvnw test -Dtest=*Service*Test` then `./mvnw verify` | Every state-machine rule has a unit test |
| M4 | `./mvnw test -Dtest=*ControllerTest` then `./mvnw verify` | Status codes + ProblemDetail shape |
| M5 | `./mvnw verify` | One IT per journey; full suite green |

**Do not proceed to the next milestone until the gate passes.** Do not “write all production code then tests”.

Local run (not a substitute for the gate): `docker compose up -d && ./mvnw spring-boot:run`.

---

## Anti-patterns (reject list)

If a change matches any item below, reject it and redo.

1. **Tests written after all production code** — tests ship in the same milestone as the code they prove.
2. **Entities returned from controllers** — no `Claim` / `Policy` / `Staff` in `@RestController` method signatures or JSON.
3. **Business invariants enforced only in Java** — if it is in the state machine table, it has a CHECK/UNIQUE/FK in Flyway as well (authz and “officer is assignee” are the exception: they are not CHECK-able without triggers; they stay in Java **and** are tested).
4. **Editing an applied migration** — add `V{n+1}__…sql`.
5. **Mocking repositories (or services) in E2E / `@SpringBootTest` journey tests.**
6. **`@SpringBootTest` where a slice suffices** — M2 `@DataJpaTest`, M3 plain Mockito, M4 `@WebMvcTest`. Full context is M0 contextLoads + M5 ITs (+ M1 Flyway IT without Spring is preferred).
7. **`spring.jpa.open-in-view: true`** (or omitting the property so it defaults true).
8. **`GenerationType.IDENTITY` / `BIGSERIAL` / `serial` PKs.**
9. **`FetchType.EAGER`** or `join fetch` sprinkled to hide OSIV problems. Use explicit `@EntityGraph` / fetch-join **only** on staff GET-by-claimNumber repository method if needed.
10. **Business rules in controllers** (status if-else, market checks). Controllers map HTTP ↔ commands.
11. **Kafka (or REST callbacks) for request/response** of submit/track/assign/decide.
12. **Splitting into multiple Spring Boot modules/services** in this timebox.
13. **Boolean `isSettled` / `isRejected` flags** instead of `ClaimStatus`.
14. **Returning stack traces, SQL, or exception messages that leak internals** in `ProblemDetail.detail` for 500s.
15. **`hibernate.ddl-auto: update` or `create-drop`** in any committed yaml.
16. **Lombok `@Data` on entities.**
17. **Catching `Exception` inside services to return null.**
18. **Shared mutable statics** for staff context (use method parameters).
19. **Pagination libraries / MapStruct / Kafka client / Redis / security starters** unless a gate is blocked without them — they are out of scope.
20. **Silent test skips** (`@Disabled` without a one-line reason pointing at an open question).

---

## Out of scope (intentional, defend in walkthrough)

- Kafka broker, Schema Registry, outbox table (port + log publisher only).
- OAuth2 / JWT / API gateway auth.
- Claimant accounts, magic links, OTP.
- Document / photo upload, FNOL email/phone ingestion.
- Payments, recovery, subrogation, fraud, salvage.
- Reassignment, escalation, dual control, four-eyes.
- Policy period, deductibles, co-insurance, endorsements.
- Cross-market manager rollup, caching, CQRS read models.
- Multi-service split, Kubernetes manifests, observability stack (beyond structured logs).
- Idempotency keys, claim-number unguessability (sequence is guessable — known limitation).

---

## Open questions

Resolved for this PRP with **ASSUMPTION:** markers above. Remaining genuine product questions (do not block implementation):

1. What are the real six APAC market codes and should Japan be a separate claims regime (language, regulation)?
2. Should officers be restricted to a line of business (MOTOR vs PROPERTY) as well as market?
3. Is reserve default-to-claimed correct, or should FNOL start at 0 until first assessment?
4. Should claimant `claimNumber` be treated as a secret, or is email+OTP required for track?
5. When Kafka is introduced, is an outbox required for exactly-once-with-DB, or is at-least-once publish-after-commit enough?
6. Manager cross-market exposure: group role vs single-market staff row?

---

## Execution note for the implementing agent

Work M0 → M5 in order. After each gate, append a short entry to `AI_JOURNAL.md`. Do not expand scope (Kafka, auth, pagination, extra entities) until M5 is green. If the time cap is hit, stop at the last green gate and document the next unstarted milestone in the journal — a red M5 with a perfect Kafka producer is a failed delivery.
