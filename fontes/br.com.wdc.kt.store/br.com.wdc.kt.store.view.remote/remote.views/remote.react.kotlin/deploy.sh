#!/bin/bash
#
# deploy.sh - Build and publish the remote view's Kotlin client to the backend
#
# Usage: ./deploy.sh
#
# This script builds the production bundle and copies it to the
# fontes/work/deployment/remote-kotlin/ directory. The backend serves it at the /remote-kotlin/ context path.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FONTES_DIR="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
BACKEND_DEPLOY_DIR="$FONTES_DIR/work/deployment/remote-kotlin"
BUILD_OUTPUT_DIR="$SCRIPT_DIR/build/dist/js/productionExecutable"

echo "=== Remote Kotlin client (React/JS) Deploy ==="
echo ""

# Step 1: Build production bundle
echo "[1/3] Building production bundle..."
cd "$FONTES_DIR"
./gradlew :view-remote-react-kotlin:jsBrowserDistribution --quiet

if [ ! -d "$BUILD_OUTPUT_DIR" ]; then
    echo "ERROR: Build output not found at $BUILD_OUTPUT_DIR"
    exit 1
fi

# Step 2: Copy to backend deploy directory
echo "[2/3] Publishing to backend /remote-kotlin/ context..."
rm -rf "$BACKEND_DEPLOY_DIR"
mkdir -p "$BACKEND_DEPLOY_DIR"
cp -r "$BUILD_OUTPUT_DIR/"* "$BACKEND_DEPLOY_DIR/"

# Step 3: Pre-compress JS and CSS files with gzip (keeping originals)
echo "[3/3] Pre-compressing JS and CSS files with gzip..."
find "$BACKEND_DEPLOY_DIR" -type f \( -name "*.js" -o -name "*.css" \) | while read -r file; do
    gzip -9 -k "$file"
    echo "  gzip: $(basename "$file") -> $(basename "$file").gz"
done

echo ""
echo "Done! Files published to: $BACKEND_DEPLOY_DIR"
echo "Access at: http://localhost:8080/remote-kotlin/"
echo ""
echo "Contents:"
ls -lh "$BACKEND_DEPLOY_DIR/"
