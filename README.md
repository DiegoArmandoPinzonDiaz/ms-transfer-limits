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
docker compose up --build
bash scripts/smoke-test.sh    # con la app arriba; verifica los criterios, traceId y Jaeger
```

API http://localhost:8080 · Health http://localhost:8080/actuator/health · Jaeger http://localhost:16686

**Opción B: JDK 27** (en `JAVA_HOME` o `PATH`; Maven viene del wrapper):

```bash
./mvnw verify                 # formato, pruebas y cobertura >= 90%
DB_URL=jdbc:postgresql://localhost:5432/transferlimits DB_USER=... DB_PASSWORD=... ./mvnw spring-boot:run
```

Hook de git para pushes manuales: `git config core.hooksPath .githooks`

## Agente utilizado

**Claude Code** (app de escritorio) con **Claude Sonnet 5.5**. Desarrollo con **prompts por fases** preparados aparte, con instrucciones de ahorro de tokens (respuestas cortas, no releer archivos, reportar solo resultados). La revisión independiente corrió como subagente aparte de solo lectura (modelo Opus).

## Estrategia

1. **Harness primero**: proyecto Maven hexagonal vacío con Spotless, JaCoCo 90%, `CLAUDE.md`, permisos, hooks, skills y subagente revisor. *"Quería que la IA trabajara con reglas y controles desde el primer archivo, no ponérselos al final."*
2. **Criterios primero**: yo definí `ACCEPTANCE-CRITERIA.md` (AC-01 a AC-17); luego OpenAPI contract-first, dominio y casos de uso.
3. **Persistencia y web**: PostgreSQL con Flyway, SQL atómico para el consumo, adaptador web y errores RFC 9457, Docker Compose.
4. **Observabilidad, seguridad y CI**: OpenTelemetry con Jaeger, `traceId` en errores, escaneo de dependencias y GitHub Actions (`build`, `security`, `docker`).
5. **Revisión y documentación**: subagente independiente, correcciones aprobadas una por una y este README.

Lo que me llamó la atención: los controles automáticos encontraron problemas reales, una alerta crítica (CVSS 9.8) en una dependencia transitiva, que resolví eliminando la dependencia en vez de suprimirla, y un archivo que faltaba en el repositorio y que habría fallado al clonarlo.

## Control humano ("la IA no es juez y parte")

- Las **reglas de negocio las definí yo**; la IA no podía inventar reglas ni casos de prueba fuera de `ACCEPTANCE-CRITERIA.md`.
- Las **pruebas se derivan de mis criterios**; cada una lleva el ID del criterio que cubre.
- El **revisor es independiente y de solo lectura**; yo aprobé qué corregir.
- Las **puertas finales son deterministas**: Maven (`verify`), formato, cobertura >= 90%, escaneo de dependencias y CI.
- **El push lo hago yo**: el agente tiene prohibido hacer push en `settings.json` (probado con un intento real, que fue bloqueado), y los hooks de Claude y de git corren `verify -Psecurity` y bloquean si algo falla.

## Más detalle

[`docs/HARNESS.md`](docs/HARNESS.md) · [`docs/DECISIONS.md`](docs/DECISIONS.md) · [`docs/known-gaps.md`](docs/known-gaps.md) · [`ACCEPTANCE-CRITERIA.md`](ACCEPTANCE-CRITERIA.md) · [`openapi.yaml`](openapi.yaml)

---

Este README lo redactó el agente a partir del trabajo realizado; yo lo revisé, corregí y aprobé.
