#!/usr/bin/env bash
# Verificacion previa al push: formato (spotless), pruebas y cobertura (JaCoCo) via mvnw verify.
# Salida 0 = ok; 2 = bloquear. Usado por el hook de Claude Code y por .githooks/pre-push.
cd "$(dirname "$0")/../.." || exit 2
source .claude/hooks/env.sh

if ! out=$(./mvnw -B -ntp verify 2>&1); then
  echo "$out" | tail -40 >&2
  echo "BLOQUEADO: mvnw verify fallo (formato, pruebas o cobertura). Corrige antes de hacer push." >&2
  exit 2
fi
echo "mvnw verify OK" >&2
exit 0
