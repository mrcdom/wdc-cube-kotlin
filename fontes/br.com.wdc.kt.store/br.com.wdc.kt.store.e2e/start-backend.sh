#!/usr/bin/env bash
#
# Starts the backend for the browser tests: a work directory of its own under build/e2e-work, with security on
# (a random JWT secret), the demo data reset on startup, and the web clients deployed in fontes/work/deployment.
# Runs in the foreground; Playwright starts and stops it (playwright.config.ts).
set -euo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
FONTES_DIR="$(cd "$E2E_DIR/../.." && pwd)"
PORT="${E2E_PORT:-8091}"
WORK="$E2E_DIR/build/e2e-work"
BACKEND="$FONTES_DIR/br.com.wdc.kt.store/br.com.wdc.kt.store.backend/build/install/backend/bin/backend"

if [ ! -x "$BACKEND" ]; then
  echo "The backend is not built: run ./prepare.sh (or ./gradlew :backend:installDist) first" >&2
  exit 1
fi

mkdir -p "$WORK/config" "$WORK/data" "$WORK/log" "$WORK/tmp"
cp "$FONTES_DIR/work/config/logback.xml" "$WORK/config/logback.xml"
# the deployed clients (native, compose, remote-kotlin), shared with the development work directory
ln -sfn "$FONTES_DIR/work/deployment" "$WORK/deployment"

cat > "$WORK/config/application.toml" <<TOML
[database]
reset = true

[server]
port = $PORT
# the backend's CORS rule lists only localhost:8080, and it refuses even its own pages on another port
cors.allowAll = true

[security]
jwt.secret = "$(openssl rand -hex 32)"
auth.requestsPerMinute = 0
TOML

exec "$BACKEND" --workdir="$WORK"
