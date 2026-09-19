# Loyalty Management System

Multi-module backend implementing the design baseline in `docs/LoyaltyDesign.md` (v2.1)
against the task book `docs/LoyaltyDevelopmentTasks.md`.

## Toolchain

- **JDK 21** (LTS). A portable Microsoft OpenJDK 21 can be dropped under `tools/`
  (git-ignored) and referenced via `JAVA_HOME`.
- **Maven 3.9.x** (the committed `mvnw` wrapper downloads it automatically).
- **Docker** (optional) for running PostgreSQL + Kafka via `docker-compose.yml`.
  The test suite does **not** need Docker — it uses Zonky embedded-postgres and
  Spring `@EmbeddedKafka`.

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

## Run (microservices)

Start Eureka first, then the services and gateway:

```bash
./mvnw -pl eureka-server spring-boot:run          # :8761 registry
./mvnw -pl access-control-service spring-boot:run # :8081
./mvnw -pl member-service spring-boot:run          # :8082
./mvnw -pl engine-service spring-boot:run          # :8083
./mvnw -pl integration-service spring-boot:run    # :8084
./mvnw -pl gateway spring-boot:run                # :8080 edge
```

All services share the `loyalty` PostgreSQL schema and run Flyway on startup.

## Modules

| Module | Port | Responsibility |
| --- | --- | --- |
| `common` | — | Shared contracts: context, error model, event envelope, web/security primitives |
| `database-migrations` | — | Flyway schema + seed migrations (single source) + migration ITs |
| `eureka-server` | 8761 | Service registry (Spring Cloud Netflix Eureka) |
| `gateway` | 8080 | Edge: JWT validation, permission pre-check, routing by path prefix |
| `access-control-service` | 8081 | OIDC/JWT, RBAC, API permission mapping, admin, audit, approval |
| `member-service` | 8082 | Member/Identity/Attribute/Merge + master-data config (Program/PointType/TierScheme/Tier/Benefit) |
| `engine-service` | 8083 | Compute core (horizontally scalable): Account + Point runtime + Tier/Benefit runtime + Rule(Drools) + Order/Event |
| `integration-service` | 8084 | External ingress, normalization, idempotency, Inbox/Outbox, Kafka |

Config-vs-runtime split: `member-service` owns master-data writes (point_type,
tier_scheme, …); `engine-service` reads config and writes runtime facts (ledger,
member_tier). `docs/` is kept out of the repository.
