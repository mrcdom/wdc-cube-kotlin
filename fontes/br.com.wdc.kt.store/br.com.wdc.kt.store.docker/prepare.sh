#!/usr/bin/env bash
#
# Builds what the image takes (the backend's distribution and the deployed web clients, through the browser tests'
# prepare.sh) and stages it in build/context, the part of the Docker build context the Dockerfile copies.
set -euo pipefail

DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"
STORE_DIR="$(cd "$DOCKER_DIR/.." && pwd)"
FONTES_DIR="$(cd "$STORE_DIR/.." && pwd)"

"$STORE_DIR/br.com.wdc.kt.store.e2e/prepare.sh"

CONTEXT="$DOCKER_DIR/build/context"
rm -rf "$CONTEXT"
mkdir -p "$CONTEXT/deployment"
cp -R "$STORE_DIR/br.com.wdc.kt.store.backend/build/install/backend" "$CONTEXT/backend"
for client in native compose remote-kotlin; do
  cp -R "$FONTES_DIR/work/deployment/$client" "$CONTEXT/deployment/$client"
done
cp "$FONTES_DIR/work/config/logback.xml" "$CONTEXT/logback.xml"
echo "Docker build context ready: $CONTEXT"
