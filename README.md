# MS-TRANSFER-LIMITS

[![CI](https://github.com/DiegoArmandoPinzonDiaz/ms-transfer-limits/actions/workflows/ci.yml/badge.svg)](https://github.com/DiegoArmandoPinzonDiaz/ms-transfer-limits/actions/workflows/ci.yml)

## Resumen

Microservicio (Java 27, Spring Boot 4.1.2, Maven, PostgreSQL) que valida si una transferencia cabe en el **límite por operación** y en el **límite diario por canal** (`MOBILE`, `WEB`, `BRANCH`). Si cabe, la aprueba y registra el consumo; si no, la rechaza sin consumir. Dos validaciones simultáneas nunca superan el límite diario. Día de negocio en `America/Mexico_City`; mensajes con Apache Commons Text.

Ejemplo: `MOBILE` con 5,000 por operación y 10,000 diario, con 8,000 ya consumidos hoy (quedan 2,000):

| Solicitud | Resultado | Por qué |
|---|---|---|
| 1,500 | **200 aprobada** (quedan 500) | cabe en la operación y en lo disponible |
| 6,000 | **422** `PER_OPERATION_LIMIT_EXCEEDED` | supera el límite por operación (5,000) |
| 1,000 | **422** `DAILY_LIMIT_EXCEEDED` | supera lo disponible del día (500) |

## Cómo ejecutarlo

**Opción A: Docker** (sin Java ni Maven):

```bash
cp .env.example .env
docker compose up --build -d
bash scripts/smoke-test.sh    # con la app arriba; verifica los criterios, traceId y Jaeger
```

API http://localhost:8080 · Health http://localhost:8080/actuator/health · Jaeger http://localhost:16686

**Opción B: JDK 27** (en `JAVA_HOME` o `PATH`; Maven viene del wrapper). Compila y corre las 106 pruebas sin base de datos:

```bash
./mvnw verify                 # formato, pruebas y cobertura >= 90%
```

Para levantar y probar la app completa, usa la Opción A.

Hook de git para pushes manuales: `git config core.hooksPath .githooks`

## Agente utilizado

**Claude Code** (app de escritorio) con **Claude Sonnet 5.5**. Desarrollo con **prompts por fases** preparados aparte, con instrucciones de ahorro de tokens (respuestas cortas, no releer archivos, reportar solo resultados). La revisión independiente corrió como subagente aparte de solo lectura (modelo Opus).

## Estrategia

1. **Harness primero**: proyecto Maven hexagonal vacío con Spotless, JaCoCo 90%, `CLAUDE.md`, permisos, hooks, skills y subagente revisor (ver la sección siguiente). *"Quería que la IA trabajara con reglas y controles desde el primer archivo, no ponérselos al final."*
2. **Criterios primero**: definí `ACCEPTANCE-CRITERIA.md` (AC-01 a AC-17); luego OpenAPI contract-first, dominio y casos de uso.
3. **Persistencia y web**: PostgreSQL con Flyway, SQL atómico para el consumo, adaptador web y errores RFC 9457, Docker Compose.
4. **Observabilidad, seguridad y CI**: OpenTelemetry con Jaeger, `traceId` en errores, escaneo de dependencias (el enunciado pedía desactivarlo para ahorrar tokens; como corre en Maven y no consume tokens, lo consulté con el evaluador y quedó activo) y GitHub Actions (`build`, `security`, `docker`).
5. **Revisión y documentación**: subagente independiente, correcciones aprobadas una por una y este README.

Lo que me llamó la atención: los controles automáticos encontraron problemas reales. Una alerta crítica (CVSS 9.8) en una dependencia transitiva, que se resolvió eliminando la dependencia en vez de suprimirla; y un archivo que faltaba en el repositorio y que habría fallado al clonarlo, que apareció al final, cuando el pipeline construyó y desplegó el proyecto desde cero.

## Harness: dónde está cada pieza

| Pieza | Archivo | Para qué sirve |
|---|---|---|
| Reglas del proyecto | [`CLAUDE.md`](CLAUDE.md) | Lo que el agente debe y no debe hacer: arquitectura, contract-first, errores RFC 9457, Commons Text seguro, sin secretos, pruebas desde los criterios |
| Permisos | [`.claude/settings.json`](.claude/settings.json) | Qué comandos puede usar el agente y cuáles tiene prohibidos (push, `rm -rf`, leer `.env`, borrar volúmenes). También registra los hooks |
| Hook de formato | [`.claude/hooks/format-java.sh`](.claude/hooks/format-java.sh) | Cada vez que el agente edita un `.java`, lo formatea con el estilo de Google |
| Hook de pre-push (Claude) | [`.claude/hooks/claude-pre-push.sh`](.claude/hooks/claude-pre-push.sh) | Si el agente intenta un push, corre pruebas, cobertura y escaneo de seguridad, y lo bloquea si algo falla |
| Hook de pre-push (git) | [`.githooks/pre-push`](.githooks/pre-push) | Lo mismo, para pushes hechos a mano |
| Skills | [`.claude/skills/`](.claude/skills/) | Recetas para tareas repetidas: `new-endpoint`, `problem-details` y `coverage-check` |
| Subagente revisor | [`.claude/agents/code-reviewer.md`](.claude/agents/code-reviewer.md) | Auditor de solo lectura: revisa calidad, concurrencia, seguridad y que las pruebas cubran los criterios. No puede modificar código |
| Criterios de aceptación | [`ACCEPTANCE-CRITERIA.md`](ACCEPTANCE-CRITERIA.md) | Los casos de negocio que definí antes de programar; cada prueba referencia uno de estos criterios |

Cómo se probó cada pieza: [`docs/HARNESS.md`](docs/HARNESS.md).

## Control humano sobre el agente

Antes de escribir una sola línea de código, trabajé con la IA en un análisis previo del enunciado: qué pedía, qué quedaba ambiguo y qué riesgos había. Con eso definí el harness según el criterio que quería para este proyecto; la IA también sugirió algunas piezas, y yo decidí cuáles entraban. No empecé la implementación hasta tener claras dos cosas: el harness funcionando (reglas, permisos, hooks, skills y revisor) y el plan de trabajo por fases.

También dejé por escrito, en `ACCEPTANCE-CRITERIA.md`, los casos que el enunciado no aclaraba (zona horaria del día, montos iguales al límite, bajar un límite por debajo de lo consumido, cuentas sin límites), para que el agente no los inventara.

Después avancé fase por fase. En cada una, el agente implementaba y corría las verificaciones, y yo revisaba el resultado antes de pasar a la siguiente: aprobaba, pedía cambios o tomaba decisiones cuando había que elegir (por ejemplo, cómo responder un rechazo o cómo resolver cada vulnerabilidad). Al final, un subagente de solo lectura revisó todo el proyecto, y yo decidí qué correcciones aplicar.

Lo que decide si el código pasa no es el modelo, sino las pruebas, la cobertura, el formato y el escaneo de seguridad. Y el push siempre lo hice yo: el agente no tiene permiso para subir código.

## Más detalle

- **Cómo se probó cada pieza del harness:** [`docs/HARNESS.md`](docs/HARNESS.md)
- **Decisiones técnicas y por qué** (endpoints, SQL, seguridad, pruebas): [`docs/DECISIONS.md`](docs/DECISIONS.md)
- **Limitaciones conocidas y lo que quedó fuera del alcance:** [`docs/known-gaps.md`](docs/known-gaps.md)
- **Criterios de aceptación** (los casos que definí antes de programar): [`ACCEPTANCE-CRITERIA.md`](ACCEPTANCE-CRITERIA.md)
- **Contrato de la API** (OpenAPI 3): [`openapi.yaml`](openapi.yaml)
- **Pruebas manuales de la API:** [`requests.http`](requests.http)
- **Pipeline de CI:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

---

Este README lo redactó el agente a partir del trabajo realizado; yo lo revisé, corregí y aprobé.