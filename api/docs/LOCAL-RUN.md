# How to run the API locally

## Prerequisites

- [uv](https://docs.astral.sh/uv/) (manages Python 3.13 and dependencies)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (runs Redis)

## 1. Start Redis

Redis for local development is defined in the repo root `docker-compose.yml` (`redis:7-alpine` on port `6379`, data persisted in the `redis_data` volume).

From the repo root (`ITDMA3-44/`):

```bash
docker compose up -d redis
```

Check it is healthy:

```bash
docker compose ps              # STATUS should show "healthy"
docker exec redis redis-cli ping   # -> PONG
```

## 2. Configure environment variables

From the `api/` folder, copy the example file:

```bash
cp .env.example .env
```

| Variable      | Local value                  | Notes                                                        |
| ------------- | ---------------------------- | ------------------------------------------------------------ |
| `ENVIRONMENT` | `dev`                        | `dev` or `prod`. `/docs`, `/redoc` and `/openapi.json` only exist in `dev`. Defaults to `prod` if unset. |
| `REDIS_URL`   | `redis://localhost:6379/0`   | Points at the docker-compose Redis. In deployed environments this is the Upstash Redis connection URL (set via GitHub environments). |

`.env` is git-ignored — never commit real credentials.

## 3. Install dependencies

From `api/`:

```bash
uv sync
```

## 4. Run the API

From `api/`:

```bash
uv run fastapi dev main.py
```

The API starts on <http://127.0.0.1:8000> with auto-reload.

## 5. Verify

- Health check: <http://127.0.0.1:8000/health> → `{"status": "ok", "environment": "dev"}`
- API docs (dev only): <http://127.0.0.1:8000/docs>

## Stopping

```bash
# Ctrl+C to stop the API, then from the repo root:
docker compose down        # stops Redis, keeps data
docker compose down -v     # stops Redis and deletes the redis_data volume
```
