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

## 3. Explore the API

With the app running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI spec: <http://localhost:8080/openapi.yaml> (source: `infrastructure/entry-points/reactive-web/src/main/resources/static/openapi.yaml`)
- Health: <http://localhost:8080/actuator/health>

The API has no authentication.

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

Run these in one terminal, in order; each step saves the new id in a shell variable for the next one. Names are unique (case-insensitive), so on a database that already has these names, change them or start from an empty database (`docker compose down -v`).

```bash
API=http://localhost:8080

# Create a franchise (201)
FRANCHISE_ID=$(curl -s -X POST $API/franchises \
  -H 'Content-Type: application/json' -d '{"name":"Burger Town"}' | jq -r .id)

# Add two branches to it (201)
DOWNTOWN_ID=$(curl -s -X POST $API/franchises/$FRANCHISE_ID/branches \
  -H 'Content-Type: application/json' -d '{"name":"Downtown"}' | jq -r .id)
UPTOWN_ID=$(curl -s -X POST $API/franchises/$FRANCHISE_ID/branches \
  -H 'Content-Type: application/json' -d '{"name":"Uptown"}' | jq -r .id)

# Add two products to Downtown (201)
BURGER_ID=$(curl -s -X POST $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products \
  -H 'Content-Type: application/json' -d '{"name":"Cheeseburger","stock":10}' | jq -r .id)
curl -s -X POST $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products \
  -H 'Content-Type: application/json' -d '{"name":"Fries","stock":25}' | jq

# Raise the Cheeseburger stock to 40 (200)
curl -s -X PATCH $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products/$BURGER_ID/stock \
  -H 'Content-Type: application/json' -d '{"stock":40}' | jq

# Top-stock report (200): Cheeseburger tops Downtown, Uptown has no products
curl -s $API/franchises/$FRANCHISE_ID/top-stock-products | jq
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
curl -s -X POST $API/franchises -H 'Content-Type: application/json' -d '{"name":"burger town"}'
# {"code":"DUPLICATE_FRANCHISE_NAME","message":"A franchise with this name already exists"}

curl -s -X PATCH $API/franchises/$FRANCHISE_ID/branches/$DOWNTOWN_ID/products/$BURGER_ID/stock \
  -H 'Content-Type: application/json' -d '{"stock":-1}'
# {"code":"INVALID_STOCK","message":"The stock must be an integer from 0 to 2147483647"}
```
