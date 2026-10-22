# AGENTS.md

Reactive Spring Boot (WebFlux + R2DBC PostgreSQL) API, generated with the Bancolombia
[Scaffold Clean Architecture](https://bancolombia.github.io/scaffold-clean-architecture/docs/intro)
plugin. Base package: `co.com.bancolombia`. Java 21.

Before writing or reviewing code, read [`CODING_STANDARDS.md`](CODING_STANDARDS.md): architecture, reactive, resilience, Java and test code rules.

## Layers and the dependency rule

Dependencies point inward only: `app-service` → `infrastructure` → `usecase` → `model`.

| Layer | Module path | Holds | May depend on |
|---|---|---|---|
| Model | `domain/model` | Entities, value objects, **gateway** interfaces (`model/<name>/gateways/`) | Nothing (only the Reactor and Lombok the root build adds) |
| UseCase | `domain/usecase` | Application logic in `usecase/<name>/<Name>UseCase.java` | `model` only |
| Driven adapters | `infrastructure/driven-adapters/*` | Gateway implementations: DB, REST clients, queues | `model` (and `usecase` if needed), any external library |
| Entry points | `infrastructure/entry-points/*` | `RouterRest` + `Handler`, request/response DTOs | `usecase`, `model`, any external library |
| App | `applications/app-service` | `MainApplication`, wiring, `application*.yaml` | Everything |

Infrastructure modules never depend on `app-service` or on each other.

## Rules per layer

**Domain (`model`, `usecase`)**
- Keep it plain Java: no Spring, R2DBC, Jackson or web types.
- Name domain classes and fields after the business (`Franchise`, `Branch`, `Product`). Technology words (`Dto`, `Request`, `Response`, `Postgres`, `R2dbc`, `Http`, `Kafka`, …) belong in infrastructure.
- Define every outside need as a gateway interface in `model/<name>/gateways/`, returning `Mono`/`Flux`.
- Use cases receive gateways through the constructor and keep only `final` fields. Leave Spring annotations off: `UseCasesConfig` registers every class in `co.com.bancolombia.usecase` whose name ends with `UseCase`.

**Driven adapters**
- An adapter implements a model gateway and owns its persistence types (`*Data` entity, Spring Data repository).
- Map persistence types to domain models inside the adapter. Domain models stay free of persistence annotations.

**Entry points**
- `RouterRest` maps routes; `Handler` parses the request, calls a use case, and builds the response.
- Request/response DTOs live in the entry point. Map them to domain models before calling the use case.
- Business rules go in the use case; the handler stays thin.

**Everywhere**
- Stay reactive end to end: compose `Mono`/`Flux`; never call `block()`.
- Inject by constructor (`@RequiredArgsConstructor` + `final` fields) in every bean.

## Adding code: generate first

Create new modules and skeletons with the plugin tasks, then fill them in. Hand-made modules miss `settings.gradle` wiring and the expected layout.

| Need | Command |
|---|---|
| Model + gateway | `./gradlew gm --name=<Name>` |
| Use case | `./gradlew guc --name=<Name>` |
| Driven adapter | `./gradlew gda --type=<type>` (`generic` needs `--name`) |
| Entry point | `./gradlew gep --type=<type>` |
| Remove a module | `./gradlew deleteModule --module=<module>` |

Task options and adapter types: [tasks docs](https://bancolombia.github.io/scaffold-clean-architecture/docs/category/tasks).

## Done means

1. `./gradlew vs` passes (layer dependencies; it also runs before every `compileJava`).
2. `./gradlew test` passes, including `ArchitectureTest` in `app-service`. Rule violations there show as `ARCHITECTURE_RULE_VIOLATED` warnings in the log; treat each one as a failure to fix. `ArchitectureTest.java` is generated: leave it unchanged.
3. New use cases, adapters and handlers have unit tests (`StepVerifier` for reactive flows, Mockito for gateways).

## Run locally

- Start the app with the `local` profile, which holds the DB settings (`adapters.r2dbc` in `application-local.yaml`):
  `./gradlew :app-service:bootRun --args='--spring.profiles.active=local'`
- PostgreSQL runs on `localhost:5435`, database `franchise_inventory`.

## Database migrations

- Flyway applies the SQL files in `infrastructure/driven-adapters/r2dbc-postgresql/src/main/resources/db/migration` on app startup (`V<n>__<description>.sql`). Never edit an applied migration; add a new one.
- Flyway uses JDBC and reads its connection from `adapters.r2dbc.*` (see `spring.flyway` in `application.yaml`), so the `local` profile and the `ADAPTERS_R2DBC_*` variables in `docker-compose.yml` configure both.
- Tables: `franchise`, `branch`, `product` (UUID ids, soft delete on `product.deleted_at`). Name uniqueness is case-insensitive; `stock >= 0`; the Top-Stock tie-break is the oldest Product. The comments in `V1` explain each rule.
