#!/usr/bin/env bash
# Pinned formatter for production code, persistence tests, and runtime harnesses.
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
formatter_version=1.32.0
formatter_sha256=b8efc4a512242392aaed8fb6d5baa5373e7050190f63dd05c67b8ec255c017ff
formatter_jar="${GOOGLE_JAVA_FORMAT_JAR:-$repo_root/.gradle/formatters/google-java-format-$formatter_version-all-deps.jar}"

case "${1:---write}" in
    --write) set -- --aosp --skip-reflowing-long-strings --replace ;;
    --check) set -- --aosp --skip-reflowing-long-strings --dry-run --set-exit-if-changed ;;
    *) echo "Usage: scripts/format-java.sh [--write|--check]" >&2; exit 2 ;;
esac

if [[ ! -f "$formatter_jar" ]]; then
    mkdir -p -- "$(dirname -- "$formatter_jar")"
    curl --fail --show-error --location \
        "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/$formatter_version/google-java-format-$formatter_version-all-deps.jar" \
        --output "$formatter_jar.download"
    mv -- "$formatter_jar.download" "$formatter_jar"
fi

if command -v sha256sum >/dev/null 2>&1; then
    actual_hash="$(sha256sum "$formatter_jar")"
else
    actual_hash="$(shasum -a 256 "$formatter_jar")"
fi
if [[ "${actual_hash%% *}" != "$formatter_sha256" ]]; then
    echo "Formatter checksum mismatch: $formatter_jar" >&2
    exit 1
fi

java_cmd=java
if [[ -n "${JAVA_HOME:-}" ]]; then
    java_cmd="$JAVA_HOME/bin/java"
fi

cd -- "$repo_root"
# Include untracked sources while excluding generated/ignored files and worlds.
git ls-files --cached --others --exclude-standard -z -- '*.java' \
    | xargs -0 "$java_cmd" -jar "$formatter_jar" "$@"
