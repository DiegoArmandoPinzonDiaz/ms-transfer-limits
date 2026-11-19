#!/usr/bin/env bash
# Hook PreToolUse (Bash): si el comando es un "git push" (tambien con opciones de git antes del
# subcomando, p. ej. git -C . push, git -c k=v push, rutas como /usr/bin/git, alias que expanden a
# push, o dentro de bash -c "..."), corre pre-push-check.sh y bloquea (exit 2) si falla.
# PUSH_DETECT_ONLY=1 solo imprime PUSH u OK (para probar la deteccion).
cd "$(dirname "$0")/../.." || exit 0
input=$(cat)
cmd=$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p')

# Alias de git que expanden a push (git config alias.x push / '!git push ...')
ALIASES=$(git config --get-regexp '^alias\.' 2>/dev/null \
  | awk '$2 ~ /(^|[ !])push( |$)/ { sub(/^alias\./, "", $1); print $1 }')

is_push() {
  local words i j n w o sub a
  # Separadores y comillas -> espacios, para ver tambien lo que va dentro de bash -c "..."
  read -r -a words <<< "$(printf '%s' "$1" | tr ';&|()"'"'"'`\\' '         ')"
  n=${#words[@]}
  for ((i = 0; i < n; i++)); do
    w=${words[i]##*/}
    case "$w" in git | git.exe) ;; *) continue ;; esac
    j=$((i + 1))
    while ((j < n)); do
      o=${words[j]}
      case "$o" in
        -C | -c | --git-dir | --work-tree | --namespace | --exec-path) j=$((j + 2)) ;;
        -*) j=$((j + 1)) ;;
        *) break ;;
      esac
    done
    sub=${words[j]:-}
    [ "$sub" = "push" ] && return 0
    for a in $ALIASES; do [ -n "$sub" ] && [ "$sub" = "$a" ] && return 0; done
  done
  return 1
}

if is_push "$cmd"; then
  [ -n "${PUSH_DETECT_ONLY:-}" ] && { echo PUSH; exit 0; }
  exec bash "$(dirname "$0")/pre-push-check.sh"
fi
[ -n "${PUSH_DETECT_ONLY:-}" ] && echo OK
exit 0
