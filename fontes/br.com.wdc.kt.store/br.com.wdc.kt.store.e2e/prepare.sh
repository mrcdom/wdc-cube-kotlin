#!/usr/bin/env bash
#
# Builds what the browser tests open: the TypeScript client's bundle (into the remote host's resources), the
# native web, Compose web and Kotlin remote clients (deployed to fontes/work/deployment), and the backend.
set -euo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
STORE_DIR="$(cd "$E2E_DIR/.." && pwd)"
FONTES_DIR="$(cd "$STORE_DIR/.." && pwd)"

(cd "$STORE_DIR/br.com.wdc.kt.store.view.remote/remote.views/remote.react.typescript" && npm ci && npm run build)
"$STORE_DIR/br.com.wdc.kt.store.view.native/native.web/deploy.sh"
"$STORE_DIR/br.com.wdc.kt.store.view.compose/compose.web/deploy.sh"
"$STORE_DIR/br.com.wdc.kt.store.view.remote/remote.views/remote.react.kotlin/deploy.sh"
(cd "$FONTES_DIR" && ./gradlew :backend:installDist ${GRADLE_ARGS:-})
