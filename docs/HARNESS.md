# El harness

Piezas que guían y controlan al agente, y cómo se probó cada una.

| Pieza | Qué hace | Cómo se probó |
|---|---|---|
| [`CLAUDE.md`](../CLAUDE.md) | Objetivo, requisitos, arquitectura hexagonal, comandos y reglas: contract-first, cero lógica de negocio en controllers, errores siempre RFC 9457, Commons Text solo con `StringSubstitutor` + Map, sin secretos, pruebas derivadas de `ACCEPTANCE-CRITERIA.md`, `./mvnw verify` antes de terminar, supresiones solo individuales | Es instrucción para el agente; se cumplió revisándolo en la revisión independiente |
| [`.claude/settings.json`](../.claude/settings.json) | Permisos: permite `mvnw`, `docker compose up/down/logs/ps/build`, `git status/diff/add/commit`; deniega leer `.env`, `rm -rf`, `git push` (incluido `--force`), `docker compose down -v`, `docker volume` y `docker system prune`. Define los dos hooks | JSON validado; los hooks se probaron con entradas simuladas (abajo) |
| Hook de formato (`PostToolUse`, `.claude/hooks/format-java.sh`) | Tras editar un `.java` corre `spotless:apply` | Un archivo mal formateado quedó con estilo Google |
| Hook de pre-push (`PreToolUse`, `claude-pre-push.sh` → `pre-push-check.sh`) | Si el comando es un `git push` (también `git -C . push`, `-c`, ruta completa, `bash -c`, alias) corre `./mvnw verify -Psecurity` y bloquea con exit 2 si algo falla | Con formato roto devolvió exit 2 y con el código limpio pasó; 14 casos de detección de push |
| Hook de git (`.githooks/pre-push`) | Llama al mismo script para pushes hechos a mano | Devolvió exit 2 con formato roto |
| 3 skills (`.claude/skills/`) | `new-endpoint` (contract-first y pruebas desde los criterios), `problem-details` (errores RFC 9457 con plantillas seguras) y `coverage-check` (clases bajo 90%; su primer paso carga `.claude/hooks/env.sh` para resolver JDK 27, commit 431fe17) | `coverage-check` se invocó como skill en una sesión de prueba: `verify` pasó y solo `ProblemExceptionHandler` quedó bajo 90% (ramas 75%, líneas 100%). `new-endpoint` y `problem-details` siguen **sin ejercitarse** |
| Subagente `code-reviewer` (`.claude/agents/`) | Solo lectura (`Read`, `Grep`, `Glob` y Bash limitado a `mvnw`); revisa estilo, diseño, concurrencia, validación, datos sensibles, Commons Text, CVE y que las pruebas cubran los criterios. Devuelve hallazgos priorizados | Se ejecutó sobre todo el repo (Fase 5, como subagente general) y, en una sesión de prueba, ya **registrado** como `code-reviewer` sobre el último commit: sin hallazgos críticos ni altos; 3 medios (el `deny` solo cubre el prefijo literal de `git push`; el texto de "tres capas" sobrevende las capas 2 y 3; la afirmación del job `docker` en verde el revisor no pudo verificarla; la verifiqué yo después en Actions) |

## Push: tres capas

1. `settings.json` deniega `git push` (y el force push). Es la única capa que lo prohíbe. Se probó con un intento real, que fue bloqueado. Cubre el prefijo literal; variantes como `git -C . push` no se probaron contra el `deny`.
2. El hook de Claude y 3. el hook de git no prohíben el push: corren `verify -Psecurity` y bloquean solo si falla.

## Notas de la sesión de prueba

La prueba del harness en uso real se hizo en una sesión aparte con Claude Sonnet 5.5.

- Intento real de `git push` desde el agente: **bloqueado por la capa 1** (`deny` de `settings.json`, antes de ejecutar nada). Los hooks de las capas 2 y 3 no llegaron a correr en ese intento.
- El shell del agente traía JDK 8/17 en `JAVA_HOME`; `./mvnw verify` falló (`--add-exports`) hasta cargar `source .claude/hooks/env.sh`. Corregido: la skill `coverage-check` ya lo carga en su primer paso (commit 431fe17).
