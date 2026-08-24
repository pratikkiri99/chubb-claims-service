# AI working journal

Running log of how AI was directed on this assessment. Not polished.

## 2026-08-24 — PRP

- Asked AI to read `requirement/candidate_assessment_brief.md` and produce `PRP.md` only (no implementation).
- Accepted the single-service slice, REST for user waits, event publisher port instead of Kafka, `X-Staff-Id` instead of OAuth.
- Challenged nothing material; the brief is underspecified on purpose.

## 2026-08-24 — M0 skeleton

- Instructed: implement PRP M0 only, gate before commit, Conventional Commit, push, then stop.
- Accepted Spring Boot **3.5.16** (latest 3.5.x OSS) instead of the PRP’s example 3.5.5. Did not jump to Boot 4 even though start.spring.io now defaults to 4.1.x.
- Overrode Initializr: hand-wrote `pom.xml` and fetched Maven Wrapper, because Initializr no longer offers 3.5.
- Added `V0__baseline.sql` (`-- baseline`) so Flyway has a migration before M1 schema, as the PRP specified.

## 2026-08-24 — M1 schema

- User asked to continue M1–M5 without stopping between gates.
- Implemented Flyway V1–V5 exactly as PRP DDL; seed UUIDs match the PRP staff ids.
- Assumption: HK officer team is `HK-MOTOR-1` (PRP named the person, not the team). Policy ids are fixed UUIDs `5555…`–`8888…` for later tests.

## 2026-08-24 — M2 persistence

- Implemented JPA entities/repos mirroring V1–V5; `@DataJpaTest` + Flyway + Testcontainers.
- Challenged PRP `@Container` on an abstract superclass: Testcontainers stopped Postgres after the first test class and later classes hit a dead Hikari pool. Overrode with a JVM-scoped container + `@DynamicPropertySource`.
- Tests: Policy/Staff/Claim repository, ClaimConstraint, ClaimNumberSequence (11) plus contextLoads.

## 2026-08-24 — M3 domain

- Implemented ClaimService/WorkloadService/ExposureService, domain exceptions, logging event publisher, Clock bean.
- State transitions live on the Claim aggregate; assign uses atomic `assignIfOpen`.
- Mockito unit tests per rule plus ClaimRepositoryQueryTest for JPQL.

## 2026-08-24 — M4 API

- REST adapters, ClaimMapper, RFC 7807 GlobalExceptionHandler, OpenAPI title/version.
- @WebMvcTest covers 201/400/401/403/404/409 and claimant JSON omitting reserveAmount.
