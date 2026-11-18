#!/usr/bin/env bash
# Resuelve JDK 27 sin tocar el JAVA_HOME del sistema.
# Orden: TRANSFERLIMITS_JAVA_HOME -> JAVA_HOME si ya es 27 -> $HOME/tools/jdk27/*
want=27
ver_of() { "$1/bin/java" -version 2>&1 | sed -n '1s/.*"\([0-9]*\).*/\1/p'; }

if [ -n "${TRANSFERLIMITS_JAVA_HOME:-}" ]; then
  export JAVA_HOME="$TRANSFERLIMITS_JAVA_HOME"
elif [ -z "${JAVA_HOME:-}" ] || [ "$(ver_of "$JAVA_HOME" 2>/dev/null)" != "$want" ]; then
  for d in "$HOME"/tools/jdk27/*/; do
    [ -x "${d}bin/java" ] && export JAVA_HOME="${d%/}" && break
  done
fi
export PATH="$JAVA_HOME/bin:$PATH"
