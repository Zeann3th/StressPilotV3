#!/usr/bin/env bash
set -euo pipefail

# StressPilot Multiplatform Launcher (Linux/macOS)
# Automatically handles AppCDS (.jsa) generation and usage.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Search for the executable JAR
JAR_FILE=$(ls "$APP_ROOT"/target/stresspilot-*-exec.jar 2>/dev/null | head -n 1 || ls "$APP_ROOT"/stresspilot-*-exec.jar 2>/dev/null | head -n 1 || true)

if [[ -z "$JAR_FILE" ]]; then
    echo "Error: Could not find stresspilot-*-exec.jar in $APP_ROOT or $APP_ROOT/target"
    exit 1
fi

JSA_FILE="${JAR_FILE%.jar}.jsa"

# 1. Staleness Check
if [[ -f "$JSA_FILE" ]]; then
    REGENERATE=false
    
    # Check if JAR is newer than JSA
    if [[ "$JAR_FILE" -nt "$JSA_FILE" ]]; then
        echo "Application update detected. Updating JVM cache..."
        REGENERATE=true
    # Check if JVM rejects the archive (version mismatch, etc.)
    elif ! java -Xshare:on -XX:SharedArchiveFile="$JSA_FILE" -version >/dev/null 2>&1; then
        echo "JVM version mismatch or incompatible cache. Updating JVM cache..."
        REGENERATE=true
    fi
    
    if [[ "$REGENERATE" == "true" ]]; then
        rm -f "$JSA_FILE"
    fi
fi

# 2. Training (if JSA missing)
if [[ ! -f "$JSA_FILE" ]]; then
    echo "First run optimization: generating JVM cache. This will take a few seconds..."
    # Captures the full Spring startup class load and then exits
    java -Dspring.context.exit=onRefresh -XX:ArchiveClassesAtExit="$JSA_FILE" -jar "$JAR_FILE" >/dev/null 2>&1 || true
    
    if [[ ! -f "$JSA_FILE" ]]; then
        echo "Warning: Failed to generate AppCDS cache. Starting normally..."
    else
        echo "Optimization complete."
    fi
fi

# 3. Execution
if [[ -f "$JSA_FILE" ]]; then
    exec java -XX:SharedArchiveFile="$JSA_FILE" -jar "$JAR_FILE" "$@"
else
    exec java -jar "$JAR_FILE" "$@"
fi
