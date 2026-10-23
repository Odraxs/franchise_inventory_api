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
