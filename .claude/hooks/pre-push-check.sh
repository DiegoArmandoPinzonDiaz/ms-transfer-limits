#!/usr/bin/env bash
# Verificacion previa al push: formato, pruebas, cobertura y escaneo de CVE (CVSS >= 7) via mvnw verify -Psecurity.
# Salida 0 = ok; 2 = bloquear. Usado por el hook de Claude Code y por .githooks/pre-push.
cd "$(dirname "$0")/../.." || exit 2
source .claude/hooks/env.sh

if ! out=$(./mvnw -B -ntp verify -Psecurity 2>&1); then
  echo "$out" | tail -40 >&2
  echo "BLOQUEADO: mvnw verify -Psecurity fallo (formato, pruebas, cobertura o vulnerabilidades CVSS >= 7). Corrige antes de hacer push." >&2
  exit 2
fi
echo "mvnw verify -Psecurity OK" >&2
exit 0
