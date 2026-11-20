# Decisiones, endpoints y calidad

## Endpoints

El enunciado decía `{accoundId}`; se usó `{accountId}` porque el nombre de la variable no cambia la URL. Los errores siempre son `application/problem+json` (RFC 9457) con `traceId`. El contrato completo está en [`openapi.yaml`](../openapi.yaml).

| Método | Ruta | Qué hace | Respuestas |
|---|---|---|---|
| GET | `/api/v1/accounts/{accountId}/limits` | Límite diario, por operación, consumido y disponible hoy de cada canal | 200, 404 (sin límites), 500 |
| PUT | `/api/v1/accounts/{accountId}/limits/{channelId}` | Crea o actualiza los límites de un canal | 200, 400 (canal o límites inválidos), 500 |
| POST | `/api/v1/accounts/{accountId}/transfer-validations` | Valida una transferencia y, si se aprueba, registra el consumo | 200 aprobada, 400 (petición inválida), 404 (sin límites), 422 (rechazo de negocio), 500 |
| GET | `/actuator/health` | Estado del servicio (lo expone Actuator) | 200 |

El 422 trae como campos de extensión `reasonCode`, `requestedAmount` y `perOperationLimit` o `availableAmount`, según el caso.

## Decisiones técnicas y por qué

- **Rechazo de negocio = 422 con `problem+json`**, con `reasonCode` y montos como extensiones; la aprobación es 200.
- **Primero el límite por operación**: si se exceden ambos límites, se reporta el de operación.
- **SQL atómico**: `INSERT ... ON CONFLICT DO UPDATE ... WHERE consumido + monto <= límite` en una sola sentencia, con aislamiento **READ COMMITTED** explícito (la segunda sentencia espera el bloqueo de fila y reevalúa con lo ya confirmado). Sin "leer y luego escribir".
- **Montos con tope de 17 dígitos enteros** (caben en `NUMERIC(19,2)`): un `1e999999999` ya no puede tumbar el servicio. `accountId` limitado a 64 caracteres con patrón.
- **Commons Text solo con `StringSubstitutor` y un Map**, sin interpoladores ni lookups (por CVE-2022-42889); los valores del usuario nunca se tratan como plantilla. `commons-text` está en 1.15.0.
- **OTLP con el sender del HttpClient del JDK**, excluyendo OkHttp: así `okhttp` y `kotlin-stdlib` (CVE-2026-53914) salen del árbol de dependencias en vez de suprimir el hallazgo.
- **Supresiones individuales y documentadas** (un CVE y un artefacto, con motivo y fecha) en `dependency-check-suppressions.xml`, solo para falsos positivos de OpenTelemetry (CVEs de las versiones JS, Go, .NET y C++). Prohibido suprimir por grupo o subir el umbral de CVSS.
- **Filtro que quita la URL real de los spans HTTP** y enmascaramiento del `accountId` (solo los últimos 4 caracteres) para no exponerlo en las trazas.
- **Escaneo de dependencias activo**: el requerimiento pedía desactivarlo para ahorrar tokens, pero corre en Maven y no consume tokens del agente; lo consulté con el evaluador y aprobó dejarlo activo.

### Problemas que tuve que resolver

1. El escaneo marcó `kotlin-stdlib` (CVSS 9.8, que llegaba por OkHttp, dependencia del exportador OTLP) y se resolvió quitando esa dependencia.
2. El job `docker` de CI falló porque `.gitignore` ignoraba `.env.example`, igual que antes había fallado por `mvnw` sin permiso de ejecución al crear el repo en Windows.

## Escaneo de seguridad

```bash
./mvnw verify -Psecurity      # OWASP dependency-check; falla con CVSS >= 7
```

Necesita red. Con una llave de la NVD en la variable de entorno `NVD_API_KEY` es mucho más rápido; la llave es gratis en https://nvd.nist.gov/developers/request-an-api-key. Sin llave puede fallar por límites de la NVD (en este proyecto no funcionó de forma fiable). Nunca pongas la llave en el repo; en CI se lee del secret `NVD_API_KEY`.

## Pruebas y calidad

- **106 pruebas** unitarias, **100% de cobertura de líneas y 93.8% de ramas** (JaCoCo exige >= 90% en `verify`). Sin Spring ni base de datos: dominio y aplicación puros, controllers con MockMvc y casos de uso simulados, persistencia con `JdbcClient` simulado, concurrencia (AC-16) con un adaptador en memoria y 50 hilos.
- **Sin pruebas de integración en el proyecto**, por requerimiento. Lo que habría cubierto una (SQL real, Flyway, arranque del contexto) se verifica en el **smoke test del job `docker` de CI**, contra PostgreSQL real: flujo completo con los códigos HTTP de cada criterio, 30 peticiones simultáneas de 100 cada una con límite diario de 1,000 (se aprueban exactamente 10 y el consumo final es 1,000), AC-06, AC-07, AC-14, montos y `accountId` inválidos, `traceId` en los errores y trazas en Jaeger. El job `docker` de CI pasó en verde con estos pasos (verificado por mí; evidencia en el [historial de Actions](https://github.com/DiegoArmandoPinzonDiaz/ms-transfer-limits/actions)).

## Ejecución: detalles

- Docker: PostgreSQL corre dentro de la red de compose y **no publica** su puerto, así que no choca con una base local. Si el 8080 o el 16686 están ocupados, cambia el mapeo en `docker-compose.yml` (p. ej. `"8081:8080"` o `"16687:16686"`) y usa esas URLs.
- **`requests.http`**: flujo manual (crear límites con PUT, consultar, aprobada, rechazo por operación, rechazo por acumulado, cuenta inexistente). Se abre con la extensión REST Client de VS Code o con el cliente HTTP de IntelliJ.
- **`scripts/smoke-test.sh`**: con la app arriba, ejecuta el flujo completo con `curl`, verifica el código HTTP esperado de cada criterio, que los errores traigan `traceId` y que Jaeger tenga las trazas. Sale con error si algo no coincide. Requiere `bash` y `curl` (en Windows, Git Bash). Si cambiaste puertos: `BASE_URL=http://localhost:8081 JAEGER_URL=http://localhost:16687 bash scripts/smoke-test.sh`.
- JDK 27: en `JAVA_HOME` o en el `PATH` (los scripts del repo también aceptan `TRANSFERLIMITS_JAVA_HOME`). Maven viene del wrapper; la primera vez descarga dependencias, así que necesita red. En Windows: `mvnw.cmd verify`.
- Para correr la app sin Docker necesitas una base PostgreSQL propia (Flyway crea las tablas al arrancar). Las trazas se exportan por OTLP/HTTP a `OTLP_TRACES_ENDPOINT` (por defecto `http://localhost:4318/v1/traces`).
