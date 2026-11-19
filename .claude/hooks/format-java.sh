#!/usr/bin/env bash
# Hook PostToolUse (Edit|Write): si el archivo editado es .java, aplica spotless:apply.
input=$(cat)
file=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p')
case "$file" in
  *.java)
    cd "$(dirname "$0")/../.." || exit 0
    source .claude/hooks/env.sh
    ./mvnw -B -ntp -q spotless:apply >&2 || echo "spotless:apply fallo" >&2
    ;;
esac
exit 0
