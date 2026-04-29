#!/usr/bin/env bash
# Usage: ./scripts/cds-train.sh <path-to-exec-jar> [extra JVM args]
# Example: ./scripts/cds-train.sh target/stresspilot-3.0.1-exec.jar
#
# Requires: the app's database must be reachable (training run connects to validate beans)
# Output: <jar-name>.jsa next to the jar
# Production use: java -XX:SharedArchiveFile=<jar-name>.jsa -jar <jar>
#
# Re-run after: new build, JVM upgrade, plugin added/removed

set -euo pipefail

JAR="${1:?Usage: $0 <exec-jar>}"
JSA="${JAR%.jar}.jsa"

# Detect plugins directory (same resolution logic as SqlDriverConfig / PluginConfig)
PILOT_HOME="${PILOT_HOME:-}"
if [[ -n "$PILOT_HOME" ]]; then
  PLUGINS_DIR="$PILOT_HOME/core/plugins"
else
  PLUGINS_DIR="${HOME}/.pilot/core/plugins"
fi

# Staleness check: warn if archive exists but jar or plugins dir is newer
if [[ -f "$JSA" ]]; then
  stale=false
  [[ "$JAR" -nt "$JSA" ]] && stale=true
  [[ -d "$PLUGINS_DIR" && "$PLUGINS_DIR" -nt "$JSA" ]] && stale=true
  if $stale; then
    echo "WARNING: existing archive $JSA may be stale (jar or plugins dir changed since last training)"
    echo "Regenerating..."
  else
    echo "Archive exists and appears up-to-date. Pass --force to retrain anyway."
    [[ "${2:-}" != "--force" ]] && exit 0
  fi
fi

echo "Training run — recording classes loaded from: $JAR"
echo "Plugins dir: ${PLUGINS_DIR} ($(ls "$PLUGINS_DIR" 2>/dev/null | wc -l | tr -d ' ') plugins)"
echo "Output archive: $JSA"

# spring.context.exit=onRefresh exits after context refresh (all beans init, @PostConstruct done)
# Captures the full Spring startup class load without needing to serve traffic
java \
  -Dspring.context.exit=onRefresh \
  -XX:ArchiveClassesAtExit="$JSA" \
  "${@:2}" \
  -jar "$JAR" || true   # non-zero exit from forced shutdown is expected

if [[ -f "$JSA" ]]; then
  echo ""
  echo "Archive created: $JSA ($(du -sh "$JSA" | cut -f1))"
  echo ""
  echo "Production start command:"
  echo "  java -XX:SharedArchiveFile=\"$JSA\" -jar \"$JAR\""
  echo ""
  echo "Retrain after: new build, JVM upgrade, or plugin changes."
else
  echo "ERROR: archive not created. Check that the app started successfully above."
  exit 1
fi
