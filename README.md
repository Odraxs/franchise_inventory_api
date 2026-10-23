# Franchise Inventory API

Reactive REST API that manages franchises, their branches and the products each branch stocks, and reports the product with the most stock in every branch of a franchise.

Built with Java 21, Spring Boot 4.1 (WebFlux + R2DBC PostgreSQL), Flyway and Resilience4j, on the Bancolombia [Scaffold Clean Architecture](https://bancolombia.github.io/scaffold-clean-architecture/docs/intro).

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| [Git](https://git-scm.com/) | any recent | Cloning the repository |
| [Docker](https://docs.docker.com/get-docker/) with Docker Compose v2 (`docker compose`) | Docker 24+, Compose 2.20+ | Both run options (PostgreSQL always runs in Docker) |
| [JDK](https://adoptium.net/) | 21 | Option 2 and the tests only. Gradle itself comes with the wrapper (`./gradlew`) |
| [curl](https://curl.se/) and [jq](https://jqlang.org/) | any recent | The smoke test walkthrough only |

The app uses host port `8080` and the database container uses host port `5436`; both must be free.

Commands below are for a POSIX shell (bash, zsh) on Linux or macOS. On Windows, use WSL or Git Bash.

## 1. Clone and configure

```bash
git clone https://github.com/Odraxs/franchise_inventory_api.git
cd franchise_inventory_api
cp .env.example .env
```

`.env` sets the database name, credentials and host port that `docker-compose.yml` uses. The defaults work as they are:

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_PROFILE` | `local` | Spring profile the app container runs with |
| `DB_NAME` | `franchise_inventory` | Database name |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Database credentials |
| `DB_HOST_PORT` | `5436` | Host port where PostgreSQL is published |

## 2. Run

Pick one option. In both, Flyway creates the schema on startup, so the database needs no manual setup.

### Option 1: everything in Docker (recommended)

```bash
docker compose up --build -d
```

This builds the app image (the first build downloads Gradle and the dependencies, so it takes a few minutes), starts PostgreSQL, waits until it is healthy, and starts the app. Check that the app is up:

```bash
curl -s http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Follow the logs with `docker compose logs -f app`. Stop everything with `docker compose down`; add `-v` to also delete the database volume.

### Option 2: PostgreSQL in Docker, app with Gradle

Use this while developing: the app starts from source with no image build.

```bash
docker compose up -d postgres
ADAPTERS_R2DBC_PORT=5436 ./gradlew :app-service:bootRun --args='--spring.profiles.active=local'
```

The `local` profile (`applications/app-service/src/main/resources/application-local.yaml`) points to `localhost:5435`, the port of a PostgreSQL server installed on the host. `ADAPTERS_R2DBC_PORT=5436` points it to the Docker container instead (use your `DB_HOST_PORT` if you changed it). If you changed the database name or credentials in `.env`, also set `ADAPTERS_R2DBC_DATABASE`, `ADAPTERS_R2DBC_USERNAME` and `ADAPTERS_R2DBC_PASSWORD`.

The app is ready when the log shows `Started MainApplication`. Stop it with `Ctrl+C` and the database with `docker compose down`.

## 3. Authentication

Every business route and `/actuator/prometheus` need HTTP Basic with the **API Credential** (a username and password) of the one trusted **Client**: the developer, the challenge evaluator, Postman or curl. These stay public, with no credentials:

- `/actuator/health/**`, so the load balancer and container health checks work
- CORS preflight (`OPTIONS`), so a browser can send the real authenticated request
- the API docs: `/swagger-ui.html` and `/openapi.yaml`

Authentication works the same in every profile, `local` included.

### Local dev API Credential

The `local` profile (`application-local.yaml`) and `docker-compose.yml` set a fixed dev pair. Use it only for local development:

| Username | Password |
|---|---|
| `inventory-client` | `local-dev-only-api-credential-do-not-use-in-aws` |

Send it with `curl -u`:

```bash
curl -s -u inventory-client:local-dev-only-api-credential-do-not-use-in-aws \
  -X POST http://localhost:8080/franchises \
  -H 'Content-Type: application/json' -d '{"name":"Pizza Palace"}'
# {"id":"…","name":"Pizza Palace"}
```

Without credentials, or with a wrong username, wrong password or malformed `Authorization` header, every business route answers the same `401` before it looks at the body:

```bash
curl -si -X POST http://localhost:8080/franchises \
  -H 'Content-Type: application/json' -d '{"name":"Pizza Palace"}'
# HTTP/1.1 401 Unauthorized
# WWW-Authenticate: Basic realm="franchise-inventory"
# …
# {"code":"UNAUTHORIZED","message":"A valid API credential is required, sent with HTTP Basic"}
```

In Swagger UI, click **Authorize** and enter the same pair to use "Try it out".

### API Credential in AWS

In the `aws` profile, the **API Credential** comes from the **API Secret**, a Secrets Manager secret that the app reads at startup. The credential never goes in code, tfvars or container environment variables. Terraform will create the secret with a random password of 32 bytes or more. That wiring (the secret, the task role permission to read it and its import in the `aws` profile) is not built yet; it comes with the AWS deployment work. The app refuses to start when the username or password is blank, so it never runs without a credential.

### Security trade-offs

- **Plain HTTP in `dev`.** In the `dev` environment the API listens on plain HTTP (port 80), so the credential travels unencrypted: HTTP Basic is only base64. This risk is accepted because `dev` holds throwaway data. `prod` requires HTTPS (an ACM certificate on the load balancer, with port 80 redirecting to 443).
- **Rotation by hand.** To rotate the credential, update the **API Secret**, then force a new ECS deployment (`aws ecs update-service --cluster <cluster> --service <service> --force-new-deployment`). Running tasks read the secret only at startup, so they keep the old credential until the new ones replace them. There is no automatic rotation, and the app accepts only one password at a time.
- **No lockout.** Failed attempts do not lock the **Client** out. The password is a 256-bit random value that cannot be guessed, and locking out the only **Client** would let any attacker take the API down. Rate limiting is a later option at the edge (AWS WAF on the load balancer); it is not built yet.
- **No authorization (roles) yet.** Any authenticated **Client** can call every route. Roles come when a second **Client** with different needs appears (another service, a read-only dashboard) or when end users are introduced: for example a `READER` that can only get the top-stock report, a `WRITER` for every change, and `403 FORBIDDEN` for a **Client** without the needed role.

## 4. Explore the API

With the app running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI spec: <http://localhost:8080/openapi.yaml> (source: `infrastructure/entry-points/reactive-web/src/main/resources/static/openapi.yaml`)
- Health: <http://localhost:8080/actuator/health>

Every route below needs the **API Credential** (see [Authentication](#3-authentication)).

| Method | Path | Does |
|---|---|---|
| `POST` | `/franchises` | Create a franchise |
| `PATCH` | `/franchises/{id}` | Rename a franchise |
| `POST` | `/franchises/{franchiseId}/branches` | Add a branch to a franchise |
| `PATCH` | `/franchises/{franchiseId}/branches/{id}` | Rename a branch |
| `POST` | `/franchises/{franchiseId}/branches/{branchId}/products` | Add a product with its initial stock to a branch |
| `PATCH` | `/franchises/{franchiseId}/branches/{branchId}/products/{id}` | Rename a product |
| `DELETE` | `/franchises/{franchiseId}/branches/{branchId}/products/{id}` | Delete a product (soft delete) |
| `PATCH` | `/franchises/{franchiseId}/branches/{branchId}/products/{id}/stock` | Update the stock of a product |
| `GET` | `/franchises/{franchiseId}/top-stock-products` | Report the product with the most stock in each branch |

### Smoke test walkthrough

Run these in one terminal, in order; each step saves the new id in a shell variable for the next one, and every call sends the local dev **API Credential**. Names are unique (case-insensitive), so on a database that already has these names, change them or start from an empty database (`docker compose down -v`).

```bash
API=http://localhost:8080
CREDENTIAL=inventory-client:local-dev-only-api-credential-do-not-use-in-aws

# Create a franchise (201)
FRANCHISE_ID=$(curl -s -u $CREDENTIAL -X POST $API/franchises \
  -H 'Content-Type: application/json' -d '{"name":"Burger Town"}' | jq -r .id)

# Add two branches to it (201)
DOWNTOWN_ID=$(curl -s -u $CREDENTIAL -X POST $API/franchises/$FRANCHISE_ID/branches \
  -H 'Content-Type: application/json' -d '{"name":"Downtown"}' | jq -r .id)
UPTOWN_ID=$(curl -s -u $CREDENTIAL -X POST $API/franchises/$FRANCHISE_ID/branches \
  -H 'Content-Type: application/json' -d '{"name":"Uptown"}' | jq -r .id)

# Add two products to Downtown (201)
BURGER_ID=$(curl -s -u $CREDENTIAL -X POST $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products \
  -H 'Content-Type: application/json' -d '{"name":"Cheeseburger","stock":10}' | jq -r .id)
curl -s -u $CREDENTIAL -X POST $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products \
  -H 'Content-Type: application/json' -d '{"name":"Fries","stock":25}' | jq

# Raise the Cheeseburger stock to 40 (200)
curl -s -u $CREDENTIAL -X PATCH $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products/$BURGER_ID/stock \
  -H 'Content-Type: application/json' -d '{"stock":40}' | jq

# Top-stock report (200): Cheeseburger tops Downtown, Uptown has no products
curl -s -u $CREDENTIAL $API/franchises/$FRANCHISE_ID/top-stock-products | jq
```

The report looks like this (with your ids):

```json
{
  "franchiseId": "…",
  "branches": [
    {
      "branchId": "…",
      "branchName": "Downtown",
      "topProduct": { "id": "…", "name": "Cheeseburger", "stock": 40 }
    },
    {
      "branchId": "…",
      "branchName": "Uptown",
      "topProduct": null
    }
  ]
}
```

To see the error contract, repeat a name in another case (`409`) or send a negative stock (`400`):

```bash
curl -s -u $CREDENTIAL -X POST $API/franchises -H 'Content-Type: application/json' -d '{"name":"burger town"}'
# {"code":"DUPLICATE_FRANCHISE_NAME","message":"A franchise with this name already exists"}

curl -s -u $CREDENTIAL -X PATCH $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products/$BURGER_ID/stock \
  -H 'Content-Type: application/json' -d '{"stock":-1}'
# {"code":"INVALID_STOCK","message":"The stock must be an integer from 0 to 2147483647"}
```

### Error contract

Every error answers with a JSON body `{"code": "...", "message": "..."}`. The code name sets the HTTP status:

| Code | Status | Example |
|---|---|---|
| `UNAUTHORIZED` | 401 | Missing, wrong or malformed **API Credential** (see [Authentication](#3-authentication)) |
| `INVALID_*` | 400 | `INVALID_REQUEST` (malformed body, wrong field type, malformed UUID), `INVALID_FRANCHISE_NAME`, `INVALID_STOCK` |
| `*_NOT_FOUND` | 404 | `FRANCHISE_NOT_FOUND`, `BRANCH_NOT_FOUND`, `PRODUCT_NOT_FOUND` |
| `DUPLICATE_*` | 409 | `DUPLICATE_BRANCH_NAME` (names are unique case-insensitively) |
| `DATABASE_UNAVAILABLE` | 503 | The database is unreachable, slow, or its circuit breaker is open |

Names are trimmed and must have 1 to 100 characters. Stock is an integer from 0 to 2147483647. The Swagger UI lists the exact codes of each endpoint.

## 5. Tests

The tests need only the JDK; they do not use the database.

```bash
./gradlew vs test
```

- `vs` (`validateStructure`) checks the layer dependency rule of the clean architecture.
- `test` runs the unit tests of every module, including `ArchitectureTest` in `app-service`. Test reports land in `<module>/build/reports/tests/test/index.html`.

## 6. Project structure

Dependencies point inward only: `app-service` → `infrastructure` → `usecase` → `model`.

```text
applications/app-service/          MainApplication, bean wiring, application*.yaml
domain/model/                      Entities (Franchise, Branch, Product), domain errors, gateway interfaces
domain/usecase/                    One use case per operation (business rules)
infrastructure/driven-adapters/
  r2dbc-postgresql/                Gateway implementations on R2DBC, Flyway migrations (db/migration)
infrastructure/entry-points/
  reactive-web/                    RouterRest + Handler, request/response DTOs, error mapping, openapi.yaml
deployment/Dockerfile              Multi-stage image build (JDK build, JRE runtime)
docker-compose.yml                 PostgreSQL + app
```

- Architecture, layer rules and how to add code: [`AGENTS.md`](AGENTS.md)
- Code standards (reactive, resilience, Java, tests): [`CODING_STANDARDS.md`](CODING_STANDARDS.md)
