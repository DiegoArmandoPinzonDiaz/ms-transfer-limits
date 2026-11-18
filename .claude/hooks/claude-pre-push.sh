#!/usr/bin/env bash
# Hook PreToolUse (Bash): si el comando contiene "git push", corre pre-push-check.sh.
input=$(cat)
cmd=$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p')
case "$cmd" in
  *"git push"*) exec bash "$(dirname "$0")/pre-push-check.sh" ;;
esac
exit 0
