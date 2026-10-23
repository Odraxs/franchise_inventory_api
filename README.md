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
