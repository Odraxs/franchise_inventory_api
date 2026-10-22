# Coding Standards

Code and architecture standards for the Franchise Inventory API. Every change must follow them.
Architecture source: the Bancolombia [Scaffold Clean Architecture](https://bancolombia.github.io/scaffold-clean-architecture/docs/intro).

## 1. Architecture (Clean / Hexagonal, Bancolombia scaffold)

### 1.1 Layers and the dependency rule

Dependencies point inward only: `app-service` → `infrastructure` → `usecase` → `model`.

| Layer | Module path | Holds | May depend on |
|---|---|---|---|
| Model | `domain/model` | Entities, value objects, domain exceptions, **gateway** interfaces (ports) in `model/<name>/gateways/` | Nothing (only the Reactor and Lombok the root build adds) |
| UseCase | `domain/usecase` | Business logic in `usecase/<name>/<Name>UseCase.java` | `model` only |
| Driven adapters | `infrastructure/driven-adapters/*` | Gateway implementations: database, REST clients, queues | `model` (and `usecase` if needed), any external library |
| Entry points | `infrastructure/entry-points/*` | `RouterRest`, `Handler`, request/response DTOs, error mapping | `usecase`, `model`, any external library |
| App | `applications/app-service` | `MainApplication`, bean wiring, `application*.yaml` | Everything |

Infrastructure modules never depend on `app-service` or on each other.

### 1.2 Domain (`model`, `usecase`)

- Business rules live only in the domain. Infrastructure translates and delegates.
- Keep the domain plain Java: no Spring, R2DBC, Jackson or web types.
- Name domain classes and fields after the business (`Franchise`, `Branch`, `Product`). Technology words (`Dto`, `Request`, `Response`, `Postgres`, `R2dbc`, `Http`, `Kafka`, …) belong in infrastructure.
- Write domain entities as Java `record`s with the Lombok Builder: `@Builder(toBuilder = true)`. Create instances with the builder, and produce a changed copy with `toBuilder()`.
- Define every outside need as a gateway interface (port) in `model/<name>/gateways/`, returning `Mono`/`Flux`.
- Express business failures as domain exceptions in `model` (for example, a business exception with an error-code enum).
- Use cases receive gateways through the constructor and keep only `final` fields. Leave Spring annotations off: `UseCasesConfig` registers every class in `co.com.bancolombia.usecase` whose name ends with `UseCase`.

### 1.3 Driven adapters

- An adapter implements a model gateway and owns its persistence types (`*Data` entity, Spring Data reactive repository).
- Map persistence types to domain models inside the adapter.
- Wrap calls that leave the process with the resilience operators in section 3.

### 1.4 Entry points

- Expose HTTP only through `RouterFunction` (`RouterRest`) and handler classes (`Handler`).
- The handler parses the request, calls one use case, and builds the response. It holds no business rules.
- Request/response DTOs are Java `record`s and live in the entry point. Map them to domain models before calling the use case.
- Map domain exceptions to HTTP status codes in the entry point, with reactive error operators.

## 2. Reactive programming (Spring WebFlux)

The flow is reactive from end to end, with no blocking point. Rules marked **(hard)** have no exceptions.

| Rule | Write this |
|---|---|
| **(hard)** HTTP layer | `RouterFunction` + `Handler`. `@RestController` is forbidden. |
| **(hard)** One paradigm | WebFlux only. Spring MVC types and servlet APIs stay out. |
| **(hard)** Never block | Compose `Mono`/`Flux` and return them. `.block()`, `blockFirst()`, `blockLast()` and `Thread.sleep` are forbidden everywhere, tests included. |
| **(hard)** Errors | `Mono.error(...)`, `onErrorResume`, `onErrorReturn`, `onErrorMap`, `doOnError`. `try`/`catch` inside a reactive flow is forbidden. |
| **(hard)** Conditional logic | `filter`, `switchIfEmpty`, `defaultIfEmpty`, `hasElement`, `filterWhen`. `if` inside a reactive flow is forbidden. |
| **(hard)** Collections | `Flux.fromIterable(...)` and Reactor operators (`map`, `filter`, `reduce`, `collectList`, `groupBy`). `java.util.stream` inside a reactive flow is forbidden. |
| `map` vs `flatMap` | `map` for a synchronous transformation. `flatMap` when the function returns a `Publisher` (`Mono`/`Flux`). |
| Combining | `zip`/`zipWith` for independent results, `merge`/`concat` for streams, `then`/`thenReturn` to sequence. |
| Missing data | `switchIfEmpty(Mono.error(new <DomainException>(...)))` instead of returning `null`. |
| Backpressure | Keep the chain lazy and return the `Publisher`, so WebFlux propagates demand. |

## 3. Resilience (Resilience4j)

Apply the full set to outbound calls (database, external services):

- **Circuit Breaker**: opens after repeated failures.
- **Timeout**: limits the maximum wait time of each operation.
- **Retry**: retries failed operations with a configured backoff.
- Wrap every database call with the shared `DatabaseResilience` component.
- Choose `idempotent` or `nonIdempotent` by whether the call is safe to repeat (an INSERT is not).

Use the Resilience4j Reactor operators (`transformDeferred(CircuitBreakerOperator.of(...))`, etc.) and keep their settings in `application*.yaml`.

## 4. Java and Lombok

- Use Java `record`s for DTOs and other transfer objects.
- Use `jakarta.*`; `javax.*` is not allowed.
- Inject by constructor in every bean: `@RequiredArgsConstructor` + `final` fields.
- Choose Lombok annotations on purpose: `@Builder(toBuilder = true)` on domain entity records, `@Value` for immutable value classes that cannot be records. Use `@Data` only where mutability is required.
- Order members inside a class: constants, fields, constructors, public methods, then private methods.
- Write all code in English: classes, methods, variables and technical comments.
- Name every literal that carries meaning (paths, path variable names, code prefixes) as a `private static final` constant. Share constants between classes in one constants class, such as `ApiPaths` for routes.

## 5. Test code

- Assert reactive flows with `StepVerifier` (`reactor-test`).
- Use JUnit 5 + Mockito; mock gateways in use case tests and use cases in handler tests.
- Leave the generated `ArchitectureTest.java` in `app-service` unchanged.
