#!/usr/bin/env bash
#
# deploy.sh — Build and deploy the Shopping Native iOS app
#
# Usage:
#   ./deploy.sh simulator         Deploy to iOS Simulator (default)
#   ./deploy.sh device            Deploy to connected iOS device
#
#   Options:
#     --release                   Build in Release mode (default: Debug)
#     --simulator-name <name>     Simulator name (default: "iPhone 16")
#     --backend-url <url>         Backend URL (default: http://localhost:8080)
#     --skip-framework            Skip rebuilding the Kotlin framework
#     --clean                     Clean build before compiling
#     --help                      Show this help
#
# Prerequisites:
#   - Xcode (with command-line tools)
#   - XcodeGen (brew install xcodegen)
#   - For device deploy: ios-deploy (brew install ios-deploy) or Xcode open
#   - JDK 21 (for Kotlin framework build)
#

set -euo pipefail

# --- Configuration ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
FONTES_DIR="$SCRIPT_DIR/../../.."
BUILD_DIR="$PROJECT_DIR/build/xcode"
SCHEME="ShoppingNativeApp"
BUNDLE_ID="br.com.wdc.shopping.native.ios"

# --- Defaults ---
TARGET="simulator"
CONFIGURATION="Debug"
SIMULATOR_NAME=""
BACKEND_URL="http://localhost:8080"
SKIP_FRAMEWORK=false
CLEAN_BUILD=false

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# --- Functions ---
info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()   { error "$*"; exit 1; }

show_help() {
    sed -n '2,/^$/{ s/^# //; s/^#//; p }' "$0"
    exit 0
}

check_prerequisites() {
    info "Checking prerequisites..."

    command -v xcodebuild >/dev/null 2>&1 || die "xcodebuild not found. Install Xcode command-line tools."
    command -v xcodegen >/dev/null 2>&1 || die "xcodegen not found. Install with: brew install xcodegen"

    if [[ "$TARGET" == "device" ]]; then
        if ! command -v ios-deploy >/dev/null 2>&1; then
            warn "ios-deploy not found. Will use 'open' with Xcode instead."
            warn "For CLI deploy, install with: brew install ios-deploy"
        fi
    fi

    # Check JDK 21
    if ! /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        die "JDK 21 not found. Install with: brew install openjdk@21"
    fi

    ok "Prerequisites OK"
}

build_kotlin_framework() {
    if [[ "$SKIP_FRAMEWORK" == true ]]; then
        info "Skipping Kotlin framework build (--skip-framework)"
        return
    fi

    info "Building Kotlin/Native framework..."

    local gradle_task
    if [[ "$TARGET" == "simulator" ]]; then
        if [[ "$CONFIGURATION" == "Release" ]]; then
            gradle_task="linkReleaseFrameworkIosSimulatorArm64"
        else
            gradle_task="linkDebugFrameworkIosSimulatorArm64"
        fi
    else
        if [[ "$CONFIGURATION" == "Release" ]]; then
            gradle_task="linkReleaseFrameworkIosArm64"
        else
            gradle_task="linkDebugFrameworkIosArm64"
        fi
    fi

    export JAVA_HOME=$(/usr/libexec/java_home -v 21)

    cd "$FONTES_DIR"
    ./gradlew ":view-native-ios:$gradle_task" --no-daemon
    cd "$PROJECT_DIR"

    ok "Kotlin framework built successfully"
}

generate_xcode_project() {
    info "Generating Xcode project with XcodeGen..."

    cd "$PROJECT_DIR"
    xcodegen generate --spec project.yml

    ok "Xcode project generated: $SCHEME.xcodeproj"
}

build_app() {
    info "Building iOS app ($CONFIGURATION for $TARGET)..."

    local destination
    if [[ "$TARGET" == "simulator" ]]; then
        # Resolve simulator UDID for precise destination
        resolve_simulator_udid
        destination="id=$SIM_UDID"
    else
        destination="generic/platform=iOS"
    fi

    local clean_flag=""
    if [[ "$CLEAN_BUILD" == true ]]; then
        clean_flag="clean"
    fi

    cd "$PROJECT_DIR"

    xcodebuild \
        $clean_flag build \
        -project "$SCHEME.xcodeproj" \
        -scheme "$SCHEME" \
        -configuration "$CONFIGURATION" \
        -destination "$destination" \
        -derivedDataPath "$BUILD_DIR" \
        COMPILER_INDEX_STORE_ENABLE=NO \
        | tail -20

    if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
        die "xcodebuild failed"
    fi

    ok "App built successfully"
}

get_app_path() {
    if [[ "$TARGET" == "simulator" ]]; then
        find "$BUILD_DIR" -path "*-iphonesimulator/$SCHEME.app" -type d | head -1
    else
        find "$BUILD_DIR" -path "*-iphoneos/$SCHEME.app" -type d | head -1
    fi
}

# Resolves the simulator UDID and stores it in SIM_UDID
SIM_UDID=""

pick_simulator() {
    info "Available iOS simulators:"
    echo ""

    local simulators
    simulators=$(xcrun simctl list devices available -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
results = []
for runtime, devices in sorted(data.get('devices', {}).items()):
    if 'iOS' in runtime:
        parts = runtime.rsplit('.iOS-', 1)
        os_ver = parts[-1].replace('-', '.') if len(parts) > 1 else runtime
        for d in devices:
            if d.get('isAvailable', False) and 'iPhone' in d['name']:
                state = '(Booted)' if d['state'] == 'Booted' else ''
                print(f\"{d['name']}|{os_ver}|{d['udid']}|{state}\")
")

    if [[ -z "$simulators" ]]; then
        die "No iPhone simulators available"
    fi

    local i=1
    local names=()
    local udids=()
    while IFS='|' read -r name os_ver udid state; do
        printf "  ${GREEN}%2d)${NC} %-28s iOS %-8s %s\n" "$i" "$name" "$os_ver" "$state"
        names+=("$name")
        udids+=("$udid")
        ((i++))
    done <<< "$simulators"

    echo ""
    printf "  Select simulator [1-%d]: " "${#names[@]}"
    read -r choice

    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [[ "$choice" -lt 1 ]] || [[ "$choice" -gt "${#names[@]}" ]]; then
        die "Invalid selection: $choice"
    fi

    local idx=$((choice - 1))
    SIMULATOR_NAME="${names[$idx]}"
    SIM_UDID="${udids[$idx]}"
    echo ""
    ok "Selected: $SIMULATOR_NAME ($SIM_UDID)"
}

resolve_simulator_udid() {
    if [[ -n "$SIM_UDID" ]]; then
        return
    fi

    # If no simulator name specified, show interactive picker
    if [[ -z "$SIMULATOR_NAME" ]]; then
        pick_simulator
        return
    fi

    SIM_UDID=$(xcrun simctl list devices available -j \
        | python3 -c "
import json, sys
name = '$SIMULATOR_NAME'
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    if 'iOS' in runtime:
        for d in devices:
            if d['name'] == name and d.get('isAvailable', False):
                print(d['udid']); sys.exit(0)
sys.exit(1)
" 2>/dev/null) || die "Simulator '$SIMULATOR_NAME' not found. List available with: xcrun simctl list devices available"

    info "Resolved simulator: $SIMULATOR_NAME ($SIM_UDID)"
}

deploy_simulator() {
    info "Deploying to simulator: $SIMULATOR_NAME..."

    resolve_simulator_udid

    # Boot if shutdown
    local sim_state
    sim_state=$(xcrun simctl list devices -j | python3 -c "
import json, sys
udid = '$SIM_UDID'
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    for d in devices:
        if d['udid'] == udid:
            print(d['state']); sys.exit(0)
print('Unknown')
")

    if [[ "$sim_state" == "Shutdown" ]]; then
        info "Booting simulator..."
        xcrun simctl boot "$SIM_UDID"
        sleep 3
    fi

    # Open Simulator.app
    open -a Simulator

    # Uninstall previous version (ignore errors)
    xcrun simctl uninstall "$SIM_UDID" "$BUNDLE_ID" 2>/dev/null || true

    # Install
    local app_path
    app_path=$(get_app_path)
    if [[ -z "$app_path" ]]; then
        die "Could not find built .app bundle"
    fi

    info "Installing $app_path..."
    xcrun simctl install "$SIM_UDID" "$app_path"

    # Launch
    info "Launching app..."
    xcrun simctl launch "$SIM_UDID" "$BUNDLE_ID"

    ok "App deployed and launched on simulator"
}

deploy_device() {
    info "Deploying to connected iOS device..."

    local app_path
    app_path=$(get_app_path)
    if [[ -z "$app_path" ]]; then
        die "Could not find built .app bundle"
    fi

    if command -v ios-deploy >/dev/null 2>&1; then
        info "Using ios-deploy..."
        ios-deploy --bundle "$app_path" --debug
    else
        warn "ios-deploy not available. Opening in Xcode for device deploy..."
        open "$PROJECT_DIR/$SCHEME.xcodeproj"
        info "Use Xcode to select your device and press Run (Cmd+R)"
    fi

    ok "Device deploy complete"
}

# --- Parse Arguments ---
while [[ $# -gt 0 ]]; do
    case "$1" in
        simulator)
            TARGET="simulator"; shift ;;
        device)
            TARGET="device"; shift ;;
        --release)
            CONFIGURATION="Release"; shift ;;
        --simulator-name)
            SIMULATOR_NAME="$2"; shift 2 ;;
        --backend-url)
            BACKEND_URL="$2"; shift 2 ;;
        --skip-framework)
            SKIP_FRAMEWORK=true; shift ;;
        --clean)
            CLEAN_BUILD=true; shift ;;
        --help|-h)
            show_help ;;
        *)
            die "Unknown argument: $1. Use --help for usage." ;;
    esac
done

# --- Main ---
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║   Shopping Native iOS — Deploy                  ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  Target:       $TARGET"
echo "║  Config:       $CONFIGURATION"
if [[ "$TARGET" == "simulator" ]]; then
    if [[ -n "$SIMULATOR_NAME" ]]; then
        echo "║  Simulator:    $SIMULATOR_NAME"
    else
        echo "║  Simulator:    (interactive selection)"
    fi
fi
echo "║  Backend URL:  $BACKEND_URL"
echo "╚══════════════════════════════════════════════════╝"
echo ""

export BACKEND_URL

check_prerequisites
build_kotlin_framework
generate_xcode_project
build_app

if [[ "$TARGET" == "simulator" ]]; then
    deploy_simulator
else
    deploy_device
fi

echo ""
ok "Done! 🎉"
