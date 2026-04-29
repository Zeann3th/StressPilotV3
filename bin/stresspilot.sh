#!/usr/bin/env bash
set -euo pipefail

# StressPilot AppCDS Cache Generator (Linux/macOS)
# Generates app.jsa for faster JVM startup. Run before launching the app.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Resolve directories (matching Spring app logic)
PILOT_HOME="${PILOT_HOME:-$HOME/.pilot}"
PLUGINS_DIR="$PILOT_HOME/core/plugins"
DRIVERS_DIR="$PILOT_HOME/core/drivers"

# Search for the executable JAR
JAR_FILE=$(ls "$APP_ROOT"/target/stresspilot-*-exec.jar 2>/dev/null | head -n 1 || ls "$APP_ROOT"/stresspilot-*-exec.jar 2>/dev/null | head -n 1 || true)

if [[ -z "$JAR_FILE" ]]; then
    echo "Error: Could not find stresspilot-*-exec.jar in $APP_ROOT or $APP_ROOT/target"
    exit 1
fi

JSA_DIR="$PILOT_HOME/core/scripts"
mkdir -p "$JSA_DIR"
JSA_FILE="$JSA_DIR/app.jsa"
SIG_FILE="$JSA_DIR/app.sig"

# Function to generate a signature of current environment
get_sig() {
    local jar_info=$(stat -c %s "$JAR_FILE" 2>/dev/null || stat -f %z "$JAR_FILE")
    local plugins_info=$(ls -AR "$PLUGINS_DIR" 2>/dev/null | md5sum | cut -d' ' -f1 || echo "none")
    local drivers_info=$(ls -AR "$DRIVERS_DIR" 2>/dev/null | md5sum | cut -d' ' -f1 || echo "none")
    echo "${jar_info}-${plugins_info}-${drivers_info}"
}

CURRENT_SIG=$(get_sig)

# 1. Check for changes
REGENERATE=false
if [[ ! -f "$JSA_FILE" || ! -f "$SIG_FILE" ]]; then
    REGENERATE=true
else
    OLD_SIG=$(cat "$SIG_FILE")
    if [[ "$CURRENT_SIG" != "$OLD_SIG" ]]; then
        echo "Environment change detected. Updating JVM cache..."
        REGENERATE=true
    elif ! java -Xshare:on -XX:SharedArchiveFile="$JSA_FILE" -version >/dev/null 2>&1; then
        echo "JVM version mismatch or incompatible cache. Updating JVM cache..."
        REGENERATE=true
    fi
fi

# 2. Training
if [[ "$REGENERATE" == "true" ]]; then
    echo "Optimizing startup for your environment. This will take a few seconds..."
    rm -f "$JSA_FILE"
    java -Dspring.context.exit=onRefresh -XX:ArchiveClassesAtExit="$JSA_FILE" -jar "$JAR_FILE" >/dev/null 2>&1 || true
    
    if [[ -f "$JSA_FILE" ]]; then
        echo "$CURRENT_SIG" > "$SIG_FILE"
        echo "Optimization complete."
    else
        echo "Warning: Failed to generate AppCDS cache."
        exit 1
    fi
fi
