# Loyalty Management System

Multi-module backend implementing the design baseline in `docs/LoyaltyDesign.md` (v2.1)
against the task book `docs/LoyaltyDevelopmentTasks.md`.

## Toolchain

- **JDK 21** (LTS). A portable Microsoft OpenJDK 21 can be dropped under `tools/`
  (git-ignored) and referenced via `JAVA_HOME`.
- **Maven 3.9.x** (the committed `mvnw` wrapper downloads it automatically).

Set the JDK before building (the wrapper respects `JAVA_HOME`):

```bash
export JAVA_HOME=/path/to/jdk-21
./mvnw -v
```

## Build

```bash
# compile + unit tests (no Docker required)
./mvnw clean verify -DskipITs

# integration tests (embedded PostgreSQL + embedded Kafka; no Docker required)
./mvnw -Pintegration verify
```

## Local infrastructure (optional)

`docker-compose.yml` provides PostgreSQL 16 and Kafka 3.7 (KRaft) for running the
platform against real services. The test suite does **not** require Docker — it uses
Zonky embedded-postgres and Spring `@EmbeddedKafka`.

```bash
docker compose up -d
```

## Run

```bash
./mvnw spring-boot:run
```

## Layout

Single Spring Boot module (`loyalty-app`). Logical service boundaries are expressed as
packages and enforced by code discipline (V1 is a shared-database, single-deployable
per the design baseline). `docs/` is kept out of the repository.

```
src/main/java/com/loyalty/
├── platform/         PlatformApplication bootstrap
├── common/            context, error, event, web contracts
├── program/           Program / PointType / TierScheme / Benefit config   (M3)
├── member/            Member / Identity / Attribute / Merge               (M3/M8)
├── account/           Account lifecycle                                   (M3)
├── point/             Ledger / Allocation / Operation / Lock runtime      (M4)
├── tier/              MemberTier / TierEvaluation                          (M7)
├── benefit/           MemberBenefit grant/revoke                           (M7)
├── rule/              RuleDefinition / RuleVersion / Drools + audit        (M6)
├── access/            Principal / Role / Permission / API mapping / audit (M2)
└── integration/       Inbox / Outbox / Kafka                               (M5)
src/main/resources/
├── db/migration/      Flyway migrations (V001..V012)
└── application.yml
```

See `docs/ImplementationReport.md` (local only) for status, deviations, and verification.
