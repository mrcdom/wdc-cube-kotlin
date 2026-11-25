# backend

The application server, on **Javalin** (port 8080).

It has four responsibilities:
- **REST API** (`/api/repo`, `/api/tx`, `/api/auth`, `/openapi.json`) — for the Compose and native clients — [architecture](../../../docs/architecture-persistence.md#rest-api)
- **WebSocket** (`/dispatcher/{id}`) — for the React client (remote view) — [architecture](../../../docs/architecture-react.md)
- **Static files** — serves the web clients: the React client at the root (`/index.html`) and each deployed one under its context (`/native/`, `/compose/`, `/remote-kotlin/`)
- **Landing page** (`/`) — what the demo is, a card per web client (a link when deployed, the command that deploys it when not), the demo users, and a warning when security is off; unknown paths come back to it (`LandingPage`, `resources/landing/index.html`)

On startup, it builds the connection pool, creates the missing tables and runs the migrations.

```bash
cd fontes && ./gradlew :backend:run
```

## Work directory

The backend **requires** a work directory: the configuration and the log come from it, and the database, the temporary files and the published frontends live in it. Pass it with the `--workdir=<folder>` argument or the `STORE_WORKDIR` environment variable; without it the service does not start.

- `./gradlew :backend:run` uses [`fontes/work`](../../work/) (another one: `-Pworkdir=/path`).
- From the IDE: add `--workdir=$PROJECT_DIR$/work` to the run configuration's *Program arguments*.
- From the distribution (`installDist`): `bin/backend --workdir=/path/work`.

The folder layout is described in [`fontes/work/README.md`](../../work/README.md).

## Configuration

The backend reads `config/application.toml` in the work directory. Without the file, it starts with the defaults: H2 in `data/` and **no security**. [`work/config/application.example.toml`](../../work/config/application.example.toml) lists every key, with the defaults:

- **Database** — H2 (default) or PostgreSQL, chosen by the `url`; connection pool; remote transactions.
- **Security** — with `security.jwt.secret`, the API requires authentication. Without it, it stays open: development only.
- **Server** — port and CORS.
- **Management** — with `management.token`, the metrics at `/metrics` (see below).
- **Cluster** — with `cluster.peers`, the other instances of this backend, to tell them of the sessions that end, the images that change and the remote view sessions that move ([running-several-instances.md](../../../docs/running-several-instances.md)). Needs `security.jwt.secret`.

The log is configured by `config/logback.xml` and written to `log/`, with rotation.

## Metrics and health

- **`GET /health/live`** (also `/health`): the process answers. **`GET /health/ready`**: the database answers (503 when it does not); with `cluster.peers`, also the mesh's connections — `peers`, the other instances connected to this one, and `links`, this one's connections to them that are ready — reported, not required. Both are open, for load balancers.
- **`GET /metrics`**, in Prometheus format, only when `management.token` is set, and only with `Authorization: Bearer <token>` (the scheme in any case; 401 otherwise; 404 without the token in the configuration). Micrometer meters:
  - the JVM: memory, GC, threads, CPU, uptime, classes;
  - `http_server_requests_seconds` by method, route (as declared, `/api/repo/product/{id}/image`) and status;
  - `store_signins_total` by outcome (`ok`, `refused`, `throttled`), `store_sessions_live`, `store_sessions_renewed_total`, `store_sessions_ended_elsewhere_total`;
  - `store_remote_sessions`, `store_remote_messages_seconds` by outcome (the remote views' WebSocket messages);
  - `store_remote_transactions_open`, `store_remote_transactions_total` by event (`begun`, `committed`, `rolledback`, `reaped`, `refused`);
  - `store_cluster_peers`, `store_cluster_links_ready`, `store_cluster_messages_sent_total` and `…_received_total` by type, `store_cluster_connections_total`.

A Prometheus job for it:

```yaml
scrape_configs:
  - job_name: wdc-store
    authorization: { type: Bearer, credentials: "<management.token>" }
    static_configs: [{ targets: ["store-a:8080", "store-b:8080"] }]
```

What the meters count, where it is not obvious:

- **Sign-ins** are counted only with security on (`security.jwt.secret`; without it the remote view checks the password in the repository). `refused`: wrong password, unknown user, or a bad, expired or foreign nonce; `throttled`: the per-user wait only (the per-address 429 of `/api/auth` never reaches the sign-in). A refused renewal is not counted.
- `store_sessions_ended_elsewhere_total` counts the notices from other instances (renewals there included), whether or not this one held the session.
- `store_remote_messages_seconds{outcome="failed"}`: a message that could not be read, or an error escaping the answer; an action that fails inside the presenter's guard is `ok` (the user sees the alert).
- `store_remote_transactions_total{event="refused"}`: refused by the caps on open transactions (429) only; `rolledback` includes a doomed commit (409).
- `store_cluster_messages_sent_total` counts each publish once, whatever the number of peers, even with none connected; `store_cluster_connections_total`, this instance's connections to others becoming ready, reconnections included.
- `store_cluster_peers` and `store_cluster_links_ready` split like the readiness check: connections from others, and to others.

Each instance reports its own; Prometheus adds them up. The decision is in [ADR 0019](../../../docs/adr/0019-metrics-and-health.md).

