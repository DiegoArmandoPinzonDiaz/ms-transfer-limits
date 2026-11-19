#!/usr/bin/env bash
# Resuelve JDK 27 de forma portable (Windows/Linux/Mac). Orden:
#   1. TRANSFERLIMITS_JAVA_HOME (si se define)
#   2. JAVA_HOME si ya es Java 27
#   3. JDK local opcional en ${TRANSFERLIMITS_TOOLS_DIR:-$HOME/tools}/jdk27/* (solo si existe)
#   4. Si nada aplica, se deja JAVA_HOME / el java del PATH tal cual.
# Maven siempre via ./mvnw.
java_major() { "$1/bin/java" -version 2>&1 | sed -n '1s/.*"\([0-9]*\).*/\1/p'; }

if [ -n "${TRANSFERLIMITS_JAVA_HOME:-}" ]; then
  export JAVA_HOME="$TRANSFERLIMITS_JAVA_HOME"
elif [ -z "${JAVA_HOME:-}" ] || [ "$(java_major "$JAVA_HOME" 2>/dev/null)" != "27" ]; then
  for d in "${TRANSFERLIMITS_TOOLS_DIR:-$HOME/tools}"/jdk27/*/; do
    if [ -x "${d}bin/java" ] || [ -x "${d}bin/java.exe" ]; then
      export JAVA_HOME="${d%/}"
      break
    fi
  done
fi
if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/bin" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
