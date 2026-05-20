#!/bin/bash
#
# deploy.sh - Build and publish compose.web (Wasm/Compose) to the backend
#
# Usage: ./deploy.sh
#
# This script builds the production bundle and copies it to the backend's
# work/deploy/compose/ directory. The backend serves it at /compose/ context path.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FONTES_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DEPLOY_DIR="$FONTES_DIR/br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.backend/work/deploy/compose"
BUILD_OUTPUT_DIR="$SCRIPT_DIR/build/dist/wasmJs/productionExecutable"

echo "=== Compose Web (Wasm/Compose) Deploy ==="
echo ""

# Step 1: Build production bundle
echo "[1/2] Building production bundle..."
cd "$FONTES_DIR"
./gradlew :view-compose-web:wasmJsBrowserDistribution --quiet

if [ ! -d "$BUILD_OUTPUT_DIR" ]; then
    echo "ERROR: Build output not found at $BUILD_OUTPUT_DIR"
    exit 1
fi

# Step 2: Copy to backend deploy directory
echo "[2/3] Publishing to backend /compose/ context..."
rm -rf "$BACKEND_DEPLOY_DIR"
mkdir -p "$BACKEND_DEPLOY_DIR"
cp -r "$BUILD_OUTPUT_DIR/"* "$BACKEND_DEPLOY_DIR/"

# Step 3: Pre-compress JS, CSS, and WASM files with gzip
echo "[3/3] Pre-compressing assets (gzip)..."
find "$BACKEND_DEPLOY_DIR" -type f \( -name '*.js' -o -name '*.css' -o -name '*.wasm' \) -exec gzip -9 -k {} \;

echo ""
echo "Done! Files published to: $BACKEND_DEPLOY_DIR"
echo "Access at: http://localhost:8080/compose/"
echo ""
echo "Contents:"
ls -lh "$BACKEND_DEPLOY_DIR/"
