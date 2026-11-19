# MS-TRANSFER-LIMITS

## Objetivo
Microservicio en Java 27 + Spring Boot 4.1.2 (Maven) que valida si una transferencia cabe en los límites de una cuenta.
- Límite diario por canal (MOBILE, WEB, BRANCH) y límite por operación.
- Aprueba si no excede el límite por operación ni el acumulado del día. Registra el consumo y rechaza lo que exceda.
- Concurrencia: dos validaciones simultáneas no pueden exceder el límite diario.
- Mensajes de rechazo con plantillas de Apache Commons Text.
- Errores RFC 9457 (Problem Details). Trazas con OpenTelemetry. Imagen Docker. Contrato OpenAPI 3. Persistencia PostgreSQL.
- Estilo Google (google-java-format). Cobertura >= 90% (líneas y ramas). Sin pruebas de integración.

Endpoints:
- `GET /api/v1/accounts/{accountId}/limits`
- `PUT /api/v1/accounts/{accountId}/limits/{channelId}`
- `POST /api/v1/accounts/{accountId}/transfer-validations`
- `GET /actuator/health`

Los criterios de aceptación de negocio los define el desarrollador en `ACCEPTANCE-CRITERIA.md`. No inventes reglas de negocio ni casos de prueba fuera de ese archivo sin preguntar.

## Arquitectura (hexagonal) — paquete base `com.lab.transferlimits`
- `domain`: modelo y reglas puras, sin Spring.
- `application`: casos de uso y puertos (in/out).
- `infrastructure.adapter.in.web`: controllers REST, manejo de errores.
- `infrastructure.adapter.out.persistence`: JDBC (`JdbcClient`) con PostgreSQL y Flyway.
Dependencias solo hacia adentro: infrastructure → application → domain.

## Comandos
```bash
./mvnw verify            # formato + pruebas + cobertura (obligatorio antes de terminar)
./mvnw test              # solo pruebas
./mvnw spotless:apply    # aplicar formato
./mvnw spring-boot:run   # correr (necesita PostgreSQL: DB_URL, DB_USER, DB_PASSWORD)
docker build -t ms-transfer-limits .
```
Requiere JDK 27 (en `JAVA_HOME`, en el `PATH` o en `TRANSFERLIMITS_JAVA_HOME`; `.claude/hooks/env.sh` lo resuelve). Maven viene del wrapper (`./mvnw`), no hace falta instalarlo.

Git hook para pushes manuales: `git config core.hooksPath .githooks`

## Reglas
- Contract-first: primero `openapi.yaml`, luego el código.
- Cero lógica de negocio en controllers (solo mapear y delegar).
- Errores siempre RFC 9457 (Problem Details).
- Commons Text solo con `StringSubstitutor` y un `Map`. Prohibido `createInterpolator` y lookups.
- Nada de secretos en código; todo por variables de entorno.
- Las pruebas se derivan de `ACCEPTANCE-CRITERIA.md`.
- Correr `./mvnw verify` antes de dar algo por terminado.
- Respuestas cortas.

## Observabilidad y seguridad
- Trazas OTel (starter oficial `spring-boot-starter-opentelemetry`, export OTLP/HTTP). Con `docker compose up --build` la UI de Jaeger queda en http://localhost:16686 (servicio `ms-transfer-limits`).
- No poner accountId completo ni montos en atributos de spans ni logs (usar `AccountMask`). Todo problem+json lleva `traceId`.
- Escaneo de CVE: `./mvnw verify -Psecurity` (OWASP dependency-check, falla con CVSS >= 7). Es lo que corre el hook de pre-push. Con llave NVD en la variable de entorno `NVD_API_KEY` es mucho mas rapido; sin llave funciona igual pero la primera descarga de la base NVD tarda bastante (se cachea en ~/.m2). Nunca poner la llave en el repo.
- CI (`.github/workflows/ci.yml`): `build` (verify), `security` (verify -Psecurity, secret `NVD_API_KEY`) y `docker` (compose up + `bash scripts/smoke-test.sh`). El smoke test tambien sirve en local con `docker compose up --build -d`.
- Supresiones de dependency-check: solo en `dependency-check-suppressions.xml`, una por CVE y artefacto, con motivo y fecha. Prohibido suprimir por grupo o subir el umbral CVSS.
