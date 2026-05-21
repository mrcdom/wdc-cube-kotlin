#!/usr/bin/env bash
#
# deploy.sh — Build and deploy the Shopping Native Android app
#
# Usage:
#   ./deploy.sh emulator          Deploy to Android emulator (default)
#   ./deploy.sh device            Deploy to connected Android device
#
#   Options:
#     --release                   Build in Release mode (default: Debug)
#     --emulator-name <name>      AVD name (default: interactive selection)
#     --backend-url <url>         Backend URL (default: http://10.0.2.2:8080 for emulator, http://<host-ip>:8080 for device)
#     --skip-build                Skip Gradle build step
#     --clean                     Clean build before compiling
#     --cold-boot                 Cold boot the emulator (wipe snapshot)
#     --fresh                     Fresh install (clear icon cache by restarting launcher)
#     --netspeed <speed>          Emulator network speed (full, lte, hsdpa, umts, edge, gprs; default: full)
#     --netdelay <delay>           Emulator network delay (none, umts, gprs, edge; default: none)
#     --help                      Show this help
#
# Prerequisites:
#   - Android SDK (with platform-tools and emulator)
#   - JDK 21
#   - At least one AVD created (for emulator deploy)
#   - USB debugging enabled (for device deploy)
#

set -euo pipefail

# --- Configuration ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FONTES_DIR="$SCRIPT_DIR/../../.."
APP_ID="br.com.wdc.shopping.nativeui.android"
GRADLE_MODULE=":view-native-android"

# --- Defaults ---
TARGET="emulator"
BUILD_TYPE="debug"
EMULATOR_NAME=""
BACKEND_URL=""
SKIP_BUILD=false
CLEAN_BUILD=false
COLD_BOOT=false
FRESH_INSTALL=false
NET_SPEED="full"
NET_DELAY="none"

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

# Locate Android SDK
find_android_sdk() {
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        echo "$ANDROID_HOME"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        echo "$ANDROID_SDK_ROOT"
    elif [[ -f "$FONTES_DIR/local.properties" ]]; then
        grep "^sdk.dir" "$FONTES_DIR/local.properties" | cut -d'=' -f2 | tr -d ' '
    elif [[ -d "$HOME/Library/Android/sdk" ]]; then
        echo "$HOME/Library/Android/sdk"
    elif [[ -d "$HOME/Android/Sdk" ]]; then
        echo "$HOME/Android/Sdk"
    else
        die "Android SDK not found. Set ANDROID_HOME or configure local.properties"
    fi
}

check_prerequisites() {
    info "Checking prerequisites..."

    ANDROID_SDK=$(find_android_sdk)
    export ANDROID_HOME="$ANDROID_SDK"

    ADB="$ANDROID_SDK/platform-tools/adb"
    EMULATOR_BIN="$ANDROID_SDK/emulator/emulator"
    AVDMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager"

    [[ -x "$ADB" ]] || die "adb not found at $ADB. Install Android platform-tools."

    if [[ "$TARGET" == "emulator" ]]; then
        [[ -x "$EMULATOR_BIN" ]] || die "emulator not found at $EMULATOR_BIN. Install Android emulator."
    fi

    # Check JDK 21
    if command -v java >/dev/null 2>&1; then
        local java_ver
        java_ver=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
        if [[ "$java_ver" -lt 21 ]]; then
            warn "Java $java_ver detected. JDK 21+ recommended."
        fi
    else
        die "Java not found. Install JDK 21."
    fi

    ok "Prerequisites OK (SDK: $ANDROID_SDK)"
}

build_apk() {
    if [[ "$SKIP_BUILD" == true ]]; then
        info "Skipping build (--skip-build)"
        return
    fi

    info "Building APK ($BUILD_TYPE)..."

    local gradle_task
    if [[ "$BUILD_TYPE" == "release" ]]; then
        gradle_task="assembleRelease"
    else
        gradle_task="assembleDebug"
    fi

    local gradle_args=("$GRADLE_MODULE:$gradle_task")

    if [[ -n "$BACKEND_URL" ]]; then
        gradle_args+=("-PbaseUrl=$BACKEND_URL")
    fi

    cd "$FONTES_DIR"

    if [[ "$CLEAN_BUILD" == true ]]; then
        info "Cleaning..."
        ./gradlew "$GRADLE_MODULE:clean"
    fi

    ./gradlew "${gradle_args[@]}" --no-daemon

    ok "APK built successfully"
}

get_apk_path() {
    local variant="$BUILD_TYPE"
    local apk_dir="$SCRIPT_DIR/build/outputs/apk/$variant"
    local apk
    apk=$(find "$apk_dir" -name "*.apk" 2>/dev/null | head -1)

    if [[ -z "$apk" ]]; then
        die "APK not found in $apk_dir. Did the build succeed?"
    fi

    echo "$apk"
}

# --- Emulator functions ---

list_avds() {
    "$EMULATOR_BIN" -list-avds 2>/dev/null | grep -v "^$"
}

pick_emulator() {
    info "Available Android emulators (AVDs):"
    echo ""

    local avds=()
    while IFS= read -r avd; do
        avds+=("$avd")
    done < <(list_avds)

    if [[ ${#avds[@]} -eq 0 ]]; then
        die "No AVDs found. Create one with: avdmanager create avd ..."
    fi

    local i=1
    for avd in "${avds[@]}"; do
        printf "  ${GREEN}%2d)${NC} %s\n" "$i" "$avd"
        ((i++))
    done

    echo ""
    printf "  Select emulator [1-%d]: " "${#avds[@]}"
    read -r choice

    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [[ "$choice" -lt 1 ]] || [[ "$choice" -gt "${#avds[@]}" ]]; then
        die "Invalid selection: $choice"
    fi

    local idx=$((choice - 1))
    EMULATOR_NAME="${avds[$idx]}"
    echo ""
    ok "Selected: $EMULATOR_NAME"
}

get_all_emulator_serials() {
    "$ADB" devices 2>/dev/null | grep "emulator-" | grep "device$" | awk '{print $1}'
}

get_serial_for_avd() {
    local target_avd="$1"
    local serial
    while IFS= read -r serial; do
        [[ -z "$serial" ]] && continue
        local avd_name
        avd_name=$("$ADB" -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r')
        if [[ "$avd_name" == "$target_avd" ]]; then
            echo "$serial"
            return
        fi
    done < <(get_all_emulator_serials)
}

apply_network_settings() {
    local serial="$1"
    local port="${serial#emulator-}"
    if [[ "$NET_SPEED" != "full" || "$NET_DELAY" != "none" ]]; then
        info "Applying network settings (speed=$NET_SPEED, delay=$NET_DELAY)..."
        local auth_token_file="$HOME/.emulator_console_auth_token"
        local auth_token=""
        if [[ -f "$auth_token_file" ]]; then
            auth_token=$(cat "$auth_token_file")
        fi
        {
            if [[ -n "$auth_token" ]]; then
                echo "auth $auth_token"
            fi
            echo "network speed $NET_SPEED"
            echo "network delay $NET_DELAY"
            echo "quit"
        } | nc localhost "$port" >/dev/null 2>&1 && ok "Network settings applied" || warn "Could not apply network settings via console"
    fi
}

start_emulator() {
    info "Starting emulator: $EMULATOR_NAME..."

    # Check if the requested AVD is already running
    local serial
    serial=$(get_serial_for_avd "$EMULATOR_NAME")

    if [[ -n "$serial" ]]; then
        ok "Emulator already running ($serial)"
        DEVICE_SERIAL="$serial"
        apply_network_settings "$serial"
        return
    fi

    # Start the requested AVD
    local emu_args=("-avd" "$EMULATOR_NAME" "-no-snapshot-load" "-netspeed" "$NET_SPEED" "-netdelay" "$NET_DELAY")
    if [[ "$COLD_BOOT" == true ]]; then
        emu_args+=("-no-snapshot")
    fi

    "$EMULATOR_BIN" "${emu_args[@]}" &>/dev/null &
    local emu_pid=$!

    info "Waiting for emulator to boot..."
    local timeout=120
    local elapsed=0
    while [[ $elapsed -lt $timeout ]]; do
        serial=$(get_serial_for_avd "$EMULATOR_NAME")
        if [[ -n "$serial" ]]; then
            local boot_complete
            boot_complete=$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
            if [[ "$boot_complete" == "1" ]]; then
                DEVICE_SERIAL="$serial"
                ok "Emulator booted ($DEVICE_SERIAL)"
                return
            fi
        fi
        sleep 2
        ((elapsed += 2))
    done

    die "Emulator did not boot within ${timeout}s"
}

deploy_emulator() {
    if [[ -z "$EMULATOR_NAME" ]]; then
        pick_emulator
    fi

    start_emulator

    install_and_launch "$DEVICE_SERIAL"
}

# --- Device functions ---

pick_device() {
    info "Connected Android devices:"
    echo ""

    local devices=()
    local models=()
    while IFS=$'\t' read -r serial state; do
        if [[ "$state" == "device" && ! "$serial" =~ ^emulator ]]; then
            local model
            model=$("$ADB" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
            devices+=("$serial")
            models+=("${model:-unknown}")
        fi
    done < <("$ADB" devices 2>/dev/null | tail -n +2 | grep -v "^$")

    if [[ ${#devices[@]} -eq 0 ]]; then
        die "No Android devices connected. Enable USB debugging and connect a device."
    fi

    if [[ ${#devices[@]} -eq 1 ]]; then
        DEVICE_SERIAL="${devices[0]}"
        ok "Using device: ${models[0]} (${devices[0]})"
        return
    fi

    local i=1
    for idx in "${!devices[@]}"; do
        printf "  ${GREEN}%2d)${NC} %-20s (%s)\n" "$i" "${models[$idx]}" "${devices[$idx]}"
        ((i++))
    done

    echo ""
    printf "  Select device [1-%d]: " "${#devices[@]}"
    read -r choice

    if ! [[ "$choice" =~ ^[0-9]+$ ]] || [[ "$choice" -lt 1 ]] || [[ "$choice" -gt "${#devices[@]}" ]]; then
        die "Invalid selection: $choice"
    fi

    local idx=$((choice - 1))
    DEVICE_SERIAL="${devices[$idx]}"
    echo ""
    ok "Selected: ${models[$idx]} ($DEVICE_SERIAL)"
}

deploy_device() {
    pick_device
    install_and_launch "$DEVICE_SERIAL"
}

# --- Common install/launch ---

install_and_launch() {
    local serial="$1"

    local apk
    apk=$(get_apk_path)

    info "Uninstalling previous version..."
    "$ADB" -s "$serial" uninstall "$APP_ID" 2>/dev/null || true

    if [[ "$FRESH_INSTALL" == true ]]; then
        info "Fresh install: clearing launcher icon cache..."
        "$ADB" -s "$serial" shell pm clear com.google.android.apps.nexuslauncher 2>/dev/null || true
        "$ADB" -s "$serial" shell pm clear com.android.launcher3 2>/dev/null || true
        sleep 2
    fi

    info "Installing APK: $(basename "$apk")..."
    "$ADB" -s "$serial" install -r "$apk" || die "Install failed"

    info "Launching app..."
    "$ADB" -s "$serial" shell am start -n "$APP_ID/.MainActivity" \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

    ok "App deployed and launched!"
}

# --- Parse Arguments ---
while [[ $# -gt 0 ]]; do
    case "$1" in
        emulator)
            TARGET="emulator"; shift ;;
        device)
            TARGET="device"; shift ;;
        --release)
            BUILD_TYPE="release"; shift ;;
        --emulator-name)
            EMULATOR_NAME="$2"; shift 2 ;;
        --backend-url)
            BACKEND_URL="$2"; shift 2 ;;
        --skip-build)
            SKIP_BUILD=true; shift ;;
        --clean)
            CLEAN_BUILD=true; shift ;;
        --cold-boot)
            COLD_BOOT=true; shift ;;
        --fresh)
            FRESH_INSTALL=true; shift ;;
        --netspeed)
            NET_SPEED="$2"; shift 2 ;;
        --netdelay)
            NET_DELAY="$2"; shift 2 ;;
        --help|-h)
            show_help ;;
        *)
            die "Unknown argument: $1. Use --help for usage." ;;
    esac
done

# --- Set default backend URL based on target ---
if [[ -z "$BACKEND_URL" ]]; then
    if [[ "$TARGET" == "emulator" ]]; then
        BACKEND_URL="http://10.0.2.2:8080"
    else
        # For device, use host machine IP on local network
        local_ip=$(ipconfig getifaddr en0 2>/dev/null || echo "localhost")
        BACKEND_URL="http://$local_ip:8080"
    fi
fi

# --- Main ---
DEVICE_SERIAL=""

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║   Shopping Native Android — Deploy              ║"
echo "╠══════════════════════════════════════════════════╣"
printf "║  Target:       %s\n" "$TARGET"
printf "║  Build:        %s\n" "$BUILD_TYPE"
if [[ "$TARGET" == "emulator" ]]; then
    if [[ -n "$EMULATOR_NAME" ]]; then
        printf "║  Emulator:     %s\n" "$EMULATOR_NAME"
    else
        printf "║  Emulator:     (interactive selection)\n"
    fi
fi
printf "║  Backend URL:  %s\n" "$BACKEND_URL"
echo "╚══════════════════════════════════════════════════╝"
echo ""

check_prerequisites
build_apk

if [[ "$TARGET" == "emulator" ]]; then
    deploy_emulator
else
    deploy_device
fi

echo ""
ok "Done! 🚀"
