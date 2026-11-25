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

The log is configured by `config/logback.xml` and written to `log/`, with rotation.
