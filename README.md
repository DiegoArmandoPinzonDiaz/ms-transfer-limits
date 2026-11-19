# MS-TRANSFER-LIMITS

[![CI](https://github.com/DiegoArmandoPinzonDiaz/ms-transfer-limits/actions/workflows/ci.yml/badge.svg)](https://github.com/DiegoArmandoPinzonDiaz/ms-transfer-limits/actions/workflows/ci.yml)

## 1. Resumen

Microservicio (Java 27, Spring Boot 4.1.2, Maven, PostgreSQL) que valida si una transferencia cabe en los límites de una cuenta: un **límite por operación** y un **límite diario por canal** (`MOBILE`, `WEB`, `BRANCH`). Si cabe, la aprueba y registra el consumo; si no, la rechaza sin consumir nada. Dos validaciones simultáneas nunca pueden superar el límite diario. El día de negocio se calcula en `America/Mexico_City`.

Ejemplo: canal `MOBILE` con límite por operación **5,000** y diario **10,000**, y 8,000 ya consumidos hoy (quedan 2,000):

| Solicitud | Resultado | Por qué |
|---|---|---|
| 1,500 | **200 aprobada** (quedan 500) | cabe en la operación y en lo disponible |
| 6,000 | **422 rechazada** `PER_OPERATION_LIMIT_EXCEEDED` | supera el límite por operación (5,000) |
| 1,000 | **422 rechazada** `DAILY_LIMIT_EXCEEDED` | supera lo disponible del día (500) |

Los rechazos no consumen límite. El mensaje sale de plantillas de Apache Commons Text, p. ej. `El monto solicitado 1000.00 excede el disponible del dia de 500.00.`

## 2. Cómo ejecutarlo

### Opción A: solo Docker (recomendada)

Requisitos: Docker con Docker Compose v2. No hace falta Java ni Maven.

```bash
cp .env.example .env          # valores de ejemplo; ajusta DB_PASSWORD si quieres
docker compose up --build
```

| Qué | URL |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Jaeger (trazas, servicio `ms-transfer-limits`) | http://localhost:16686 |

PostgreSQL corre dentro de la red de compose y **no publica** su puerto, así que no choca con una base local. Si el 8080 o el 16686 están ocupados, cambia el mapeo en `docker-compose.yml` (p. ej. `"8081:8080"` o `"16687:16686"`) y usa esas URLs.

Para probarlo:

- **`requests.http`**: flujo manual (crear límites con PUT, consultar, aprobada, rechazo por operación, rechazo por acumulado, cuenta inexistente). Se abre con la extensión REST Client de VS Code o con el cliente HTTP de IntelliJ.
- **`scripts/smoke-test.sh`**: con la app arriba, ejecuta el flujo completo con `curl`, verifica el código HTTP esperado de cada criterio, que los errores traigan `traceId` y que Jaeger tenga las trazas. Sale con error si algo no coincide. Requiere `bash` y `curl` (en Windows, Git Bash).

```bash
bash scripts/smoke-test.sh
# si cambiaste puertos: BASE_URL=http://localhost:8081 JAEGER_URL=http://localhost:16687 bash scripts/smoke-test.sh
```

### Opción B: con JDK 27

Requisitos: JDK 27 en `JAVA_HOME` o en el `PATH` (los scripts del repo también aceptan `TRANSFERLIMITS_JAVA_HOME`). Maven viene del wrapper; la primera vez descarga dependencias, así que necesita red.

```bash
./mvnw verify            # Windows: mvnw.cmd verify  (formato, pruebas y cobertura >= 90%)
```

Para correr la app necesitas una base PostgreSQL propia (Flyway crea las tablas al arrancar):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/transferlimits
export DB_USER=...   DB_PASSWORD=...
./mvnw spring-boot:run
```

Las trazas se exportan por OTLP/HTTP a `OTLP_TRACES_ENDPOINT` (por defecto `http://localhost:4318/v1/traces`).

### Hook de git

Para que un `git push` hecho a mano corra las verificaciones, actívalo una vez por clon:

```bash
git config core.hooksPath .githooks
```

### Escaneo de seguridad

```bash
./mvnw verify -Psecurity      # OWASP dependency-check; falla con CVSS >= 7
```

Necesita red. Con una llave de la NVD en la variable de entorno `NVD_API_KEY` es mucho más rápido; la llave es gratis en https://nvd.nist.gov/developers/request-an-api-key. Sin llave puede fallar por límites de la NVD (en este proyecto no funcionó de forma fiable). Nunca pongas la llave en el repo; en CI se lee del secret `NVD_API_KEY`.

## 3. Endpoints

El enunciado decía `{accoundId}`; se usó `{accountId}` porque el nombre de la variable no cambia la URL. Los errores siempre son `application/problem+json` (RFC 9457) con `traceId`. El contrato completo está en [`openapi.yaml`](openapi.yaml).

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| GET | `/api/v1/accounts/{accountId}/limits` | Límite diario, por operación, consumido y disponible hoy de cada canal | 200, 404 (sin límites), 500 |
| PUT | `/api/v1/accounts/{accountId}/limits/{channelId}` | Crea o actualiza los límites de un canal | 200, 400 (canal o límites inválidos), 500 |
| POST | `/api/v1/accounts/{accountId}/transfer-validations` | Valida una transferencia y, si se aprueba, registra el consumo | 200 aprobada, 400 (petición inválida), 404 (sin límites), 422 (rechazo de negocio), 500 |
| GET | `/actuator/health` | Estado del servicio (lo expone Actuator) | 200 |

El 422 trae como campos de extensión `reasonCode`, `requestedAmount` y `perOperationLimit` o `availableAmount`, según el caso.

## 4. Agente utilizado

**Claude Code (app de escritorio)**, con el modelo **Claude Sonnet 5.5** (`claude-sonnet-5-5`) en la sesión de desarrollo. El desarrollo se hizo con **prompts por fases preparados aparte** (no están en este repositorio), con instrucciones de ahorro de tokens: respuestas cortas, no releer archivos innecesarios y reportar solo resultados. La revisión independiente de la Fase 5 corrió como subagente aparte (con un modelo Opus).

## 5. Estrategia paso a paso

1. **Fase 1: primero el harness, después el código.** Se creó el proyecto Maven hexagonal vacío con Spotless (Google Java Format), JaCoCo al 90%, `CLAUDE.md`, permisos, hooks, skills y el subagente revisor, sin lógica de negocio. Motivo, en mis palabras: *"Quería que la IA trabajara con reglas y controles desde el primer archivo, no ponérselos al final."*
2. **Fase 2: criterios primero.** Yo definí los criterios de aceptación ([`ACCEPTANCE-CRITERIA.md`](ACCEPTANCE-CRITERIA.md), AC-01 a AC-17) antes de que la IA escribiera código. Después vinieron el contrato OpenAPI (contract-first, con interfaces y DTOs generados), el dominio (`Money` con 2 decimales, límites, resultado de validación) y los casos de uso con un puerto de salida atómico y un `Clock` en `America/Mexico_City`.
3. **Fase 3: persistencia y web.** PostgreSQL con Flyway, una sentencia SQL atómica para el consumo (concurrencia), el adaptador web que implementa las interfaces generadas y el manejo global de errores RFC 9457. Docker Compose.
4. **Fase 4: observabilidad, seguridad y CI.** OpenTelemetry con Jaeger, `traceId` en los errores, escaneo de dependencias (perfil `security`) y GitHub Actions con tres jobs: `build`, `security` y `docker` (compose + smoke test).
5. **Fase 5: revisión y documentación.** Revisión con un subagente independiente de solo lectura (código, pruebas contra los criterios, secretos en el historial, prueba de clon limpio), correcciones aprobadas una por una y este README.

Lo que más me sorprendió, en mis palabras: *"Que los controles automáticos encontraron problemas reales: una alerta crítica (CVSS 9.8) en una dependencia transitiva, que resolví eliminando la dependencia en vez de suprimirla, y un archivo que faltaba en el repositorio y que habría fallado al clonarlo."* En concreto: el escaneo marcó `kotlin-stdlib` (CVSS 9.8, que llegaba por OkHttp, dependencia del exportador OTLP) y se resolvió quitando esa dependencia; y el job `docker` de CI falló porque `.gitignore` ignoraba `.env.example`, igual que antes había fallado por `mvnw` sin permiso de ejecución al crear el repo en Windows.

## 6. El harness

| Pieza | Qué hace | Cómo se probó |
|---|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Objetivo, requisitos, arquitectura hexagonal, comandos y reglas: contract-first, cero lógica de negocio en controllers, errores siempre RFC 9457, Commons Text solo con `StringSubstitutor` + Map, sin secretos, pruebas derivadas de `ACCEPTANCE-CRITERIA.md`, `./mvnw verify` antes de terminar, supresiones solo individuales | Es instrucción para el agente; se cumplió revisándolo en la revisión independiente |
| [`.claude/settings.json`](.claude/settings.json) | Permisos: permite `mvnw`, `docker compose up/down/logs/ps/build`, `git status/diff/add/commit`; deniega leer `.env`, `rm -rf`, `git push` (incluido `--force`), `docker compose down -v`, `docker volume` y `docker system prune`. Define los dos hooks | JSON validado; los hooks se probaron con entradas simuladas (abajo) |
| Hook de formato (`PostToolUse`, `.claude/hooks/format-java.sh`) | Tras editar un `.java` corre `spotless:apply` | Un archivo mal formateado quedó con estilo Google |
| Hook de pre-push (`PreToolUse`, `claude-pre-push.sh` → `pre-push-check.sh`) | Si el comando es un `git push` (también `git -C . push`, `-c`, ruta completa, `bash -c`, alias) corre `./mvnw verify -Psecurity` y bloquea con exit 2 si algo falla | Con formato roto devolvió exit 2 y con el código limpio pasó; 14 casos de detección de push |
| Hook de git (`.githooks/pre-push`) | Llama al mismo script para pushes hechos a mano | Devolvió exit 2 con formato roto |
| 3 skills (`.claude/skills/`) | `new-endpoint` (contract-first y pruebas desde los criterios), `problem-details` (errores RFC 9457 con plantillas seguras) y `coverage-check` (clases bajo 90%) | Escritas; **no se ejercitaron como skills** durante el desarrollo |
| Subagente `code-reviewer` (`.claude/agents/`) | Solo lectura (`Read`, `Grep`, `Glob` y Bash limitado a `mvnw`); revisa estilo, diseño, concurrencia, validación, datos sensibles, Commons Text, CVE y que las pruebas cubran los criterios. Devuelve hallazgos priorizados | Se ejecutó una vez sobre todo el repo (Fase 5); ver nota |

Nota: en la Fase 5 la revisión se lanzó como un subagente general que siguió la definición de `.claude/agents/code-reviewer.md`, porque ese tipo de agente no estaba registrado en la sesión de desarrollo.

## 7. Control humano ("la IA no es juez y parte")

- Las **reglas de negocio las definí yo** (`ACCEPTANCE-CRITERIA.md`); la IA no podía inventar reglas ni casos de prueba fuera de ese archivo sin preguntar.
- Las **pruebas se derivan de mis criterios** y cada una lleva el ID del criterio que cubre; las que no verifican un criterio se marcan "Técnica:".
- El **revisor es independiente y de solo lectura**: no modifica código; sus hallazgos se me presentaron en una tabla y yo aprobé qué corregir.
- Las **puertas finales son deterministas**, no del modelo: Maven (`verify`), formato, cobertura >= 90%, escaneo de dependencias y CI.
- **El push lo hago yo; el agente tiene prohibido hacer push**, en tres capas: (1) `settings.json` deniega `git push` (y el force push); (2) el hook de Claude, si de todos modos se intentara, corre `verify -Psecurity` y bloquea si falla; (3) el hook de git hace lo mismo para los pushes hechos a mano.
- **Cada decisión relevante la aprobé yo** (rechazo 422, supresiones de falsos positivos, descargas, cambios de dependencias).

## 8. Decisiones técnicas y por qué

- **Rechazo de negocio = 422 con `problem+json`**, con `reasonCode` y montos como extensiones; la aprobación es 200.
- **Primero el límite por operación**: si se exceden ambos límites, se reporta el de operación.
- **SQL atómico**: `INSERT ... ON CONFLICT DO UPDATE ... WHERE consumido + monto <= límite` en una sola sentencia, con aislamiento **READ COMMITTED** explícito (la segunda sentencia espera el bloqueo de fila y reevalúa con lo ya confirmado). Sin "leer y luego escribir".
- **Montos con tope de 17 dígitos enteros** (caben en `NUMERIC(19,2)`): un `1e999999999` ya no puede tumbar el servicio. `accountId` limitado a 64 caracteres con patrón.
- **Commons Text solo con `StringSubstitutor` y un Map**, sin interpoladores ni lookups (por CVE-2022-42889); los valores del usuario nunca se tratan como plantilla. `commons-text` está en 1.15.0.
- **OTLP con el sender del HttpClient del JDK**, excluyendo OkHttp: así `okhttp` y `kotlin-stdlib` (CVE-2026-53914) salen del árbol de dependencias en vez de suprimir el hallazgo.
- **Supresiones individuales y documentadas** (un CVE y un artefacto, con motivo y fecha) en `dependency-check-suppressions.xml`, solo para falsos positivos de OpenTelemetry (CVEs de las versiones JS, Go, .NET y C++). Prohibido suprimir por grupo o subir el umbral de CVSS.
- **Filtro que quita la URL real de los spans HTTP** y enmascaramiento del `accountId` (solo los últimos 4 caracteres) para no exponerlo en las trazas.
- **Escaneo de dependencias activo**: el requerimiento pedía desactivarlo para ahorrar tokens, pero corre en Maven y no consume tokens del agente; lo consulté con el evaluador y aprobó dejarlo activo.

## 9. Pruebas y calidad

- **106 pruebas** unitarias, **100% de cobertura de líneas y 93.8% de ramas** (JaCoCo exige >= 90% en `verify`). Sin Spring ni base de datos: dominio y aplicación puros, controllers con MockMvc y casos de uso simulados, persistencia con `JdbcClient` simulado, concurrencia (AC-16) con un adaptador en memoria y 50 hilos.
- **Sin pruebas de integración en el proyecto**, por requerimiento. Lo que habría cubierto una (SQL real, Flyway, arranque del contexto) se verifica en el **smoke test del job `docker` de CI**, contra PostgreSQL real: flujo completo con los códigos HTTP de cada criterio, 30 validaciones simultáneas sobre el mismo canal (la suma aprobada nunca supera el diario; deben ser exactamente 10 de 100 con límite 1,000), AC-06, AC-07, AC-14, montos y `accountId` inválidos, `traceId` en los errores y trazas en Jaeger. El job `docker` de CI pasó en verde con estos pasos.

## 10. Limitaciones conocidas

Detalle en [`docs/known-gaps.md`](docs/known-gaps.md). En resumen:

- La atomicidad del SQL contra PostgreSQL real solo se comprueba en CI con el smoke test, no con pruebas del proyecto.
- Los endpoints no tienen autenticación ni autorización (fuera del alcance).
- Las actions de GitHub y las imágenes de Docker están fijadas por tag, no por SHA o digest.
- No hay prueba de que la consulta (GET) muestre consumo 0 después de la medianoche, y cambiar el aislamiento a REPEATABLE READ/SERIALIZABLE exigiría reintentar errores 40001.
