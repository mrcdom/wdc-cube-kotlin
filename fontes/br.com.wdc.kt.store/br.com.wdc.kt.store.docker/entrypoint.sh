#!/usr/bin/env bash
# Writes the backend's configuration from the environment (secrets included: it never goes into the image) and starts
# it on the work directory /work.
set -euo pipefail

: "${STORE_JWT_SECRET:?STORE_JWT_SECRET is required}"

peers=""
if [ -n "${STORE_CLUSTER_PEERS:-}" ]; then
  # "ws://a:8080,ws://b:8080" → ["ws://a:8080", "ws://b:8080"]
  peers="peers = [$(printf '%s' "$STORE_CLUSTER_PEERS" | sed 's/[^,][^,]*/"&"/g; s/,/, /g')]"
fi

cat > /work/config/application.toml <<TOML
[database]
url = "${STORE_DB_URL:?STORE_DB_URL is required}"
username = "${STORE_DB_USER:-store}"
password = "${STORE_DB_PASSWORD:?STORE_DB_PASSWORD is required}"
reset = ${STORE_DB_RESET:-false}

[server]
port = 8080

[security]
jwt.secret = "$STORE_JWT_SECRET"
auth.requestsPerMinute = ${STORE_AUTH_REQUESTS_PER_MINUTE:-60}

[management]
token = "${STORE_MANAGEMENT_TOKEN:-}"

[cluster]
$peers
TOML

exec /app/bin/backend --workdir=/work
