# The WDC Store in Docker

Two backend instances behind nginx, as [running-several-instances.md](../../../docs/running-several-instances.md) describes:

| Service | What | Port |
|---|---|---|
| `postgres` | PostgreSQL 17, the shared database | — |
| `store-a`, `store-b` | the backend, on its mesh (`cluster.peers`) | 8081, 8082 (direct, for checks) |
| `nginx` | the one address: affinity (a tab's socket by session id, an API client by `X-Client-Id`) and the `/api/auth` limit per address | **8080** |
| `prometheus` | scrapes both instances' `/metrics` with the management token | 9090 |

## Running it

```bash
cp .env.example .env    # fill in three secrets: openssl rand -hex 24
./prepare.sh            # builds the backend and the web clients with Gradle, and stages them in build/context
docker compose up -d --build --wait
python3 check.py        # checks it end to end
```

Then open http://localhost:8080: the landing page, with every web client. Sign in as one of the demo users (`admin`/`admin`, `fulano`/`fulano`).

`docker compose down` stops it; add `-v` to drop the database as well.

## How it is built

- **The image takes what Gradle built.** It contains the backend's distribution (with the TypeScript remote client inside) and the deployed web clients, copied by the [`Dockerfile`](Dockerfile) onto a Temurin 21 JRE. It has no JDK, Node or Android SDK.
- **Configuration comes from the environment.** [`entrypoint.sh`](entrypoint.sh) writes `config/application.toml` from the environment at start, so no secret goes into the image.
- **The secrets live in `.env`,** which git ignores and `.dockerignore` keeps out of the build context. Prometheus reads the token as a compose secret.
- **Startup order:** the first instance creates the tables and loads the demo data on an empty database, and the second waits for it to be healthy.
- **The backends' own `/api/auth` limit is off** (`auth.requestsPerMinute = 0`). Behind a proxy, every client comes from the proxy's address, so nginx does the limiting.

## What `check.py` checks

It needs only Python.
1. nginx answers, and both instances are ready.
2. Each instance sees the other on the mesh.
3. A sign-in through nginx works, and its token works on both instances.
4. After a sign-out on one instance, the other refuses the token.
5. nginx answers 429 to a burst on `/api/auth`.
6. Prometheus scrapes both instances.

The CI's `docker` job runs it on every push, then runs the browser tests through nginx: `E2E_PORT=8080 E2E_REUSE=1 npx playwright test` in `br.com.wdc.kt.store.e2e`.

The images are pinned:
- `eclipse-temurin:21.0.13_9-jre-noble` (arm64 and amd64);
- `postgres:17.12`;
- `nginx:1.30.6`;
- `prom/prometheus:v3.16.0`.
