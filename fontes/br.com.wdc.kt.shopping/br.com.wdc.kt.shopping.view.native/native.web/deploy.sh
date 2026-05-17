#!/bin/bash
#
# deploy.sh - Build and publish native.web (React/JS) to the backend
#
# Usage: ./deploy.sh
#
# This script builds the production bundle and copies it to the backend's
# work/deploy/native/ directory. The backend serves it at /native/ context path.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FONTES_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BACKEND_DEPLOY_DIR="$FONTES_DIR/br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.backend/work/deploy/native"
BUILD_OUTPUT_DIR="$SCRIPT_DIR/build/dist/js/productionExecutable"

echo "=== Native Web (React/JS) Deploy ==="
echo ""

# Step 1: Build production bundle
echo "[1/2] Building production bundle..."
cd "$FONTES_DIR"
./gradlew :view-native-web:jsBrowserDistribution --quiet

if [ ! -d "$BUILD_OUTPUT_DIR" ]; then
    echo "ERROR: Build output not found at $BUILD_OUTPUT_DIR"
    exit 1
fi

# Step 2: Copy to backend deploy directory
echo "[2/3] Publishing to backend /native/ context..."
rm -rf "$BACKEND_DEPLOY_DIR"
mkdir -p "$BACKEND_DEPLOY_DIR"
cp -r "$BUILD_OUTPUT_DIR/"* "$BACKEND_DEPLOY_DIR/"

# Step 3: Pre-compress JS and CSS files with gzip (keeping originals)
echo "[3/3] Pre-compressing JS and CSS files with gzip..."
COMPRESSED_COUNT=0
find "$BACKEND_DEPLOY_DIR" -type f \( -name "*.js" -o -name "*.css" \) | while read -r file; do
    gzip -9 -k "$file"
    echo "  gzip: $(basename "$file") -> $(basename "$file").gz"
    COMPRESSED_COUNT=$((COMPRESSED_COUNT + 1))
done

echo ""
echo "Done! Files published to: $BACKEND_DEPLOY_DIR"
echo "Access at: http://localhost:8080/native/"
echo ""
echo "Contents:"
ls -lh "$BACKEND_DEPLOY_DIR/"
