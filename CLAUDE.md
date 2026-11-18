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
- `infrastructure.adapter.out.persistence`: JPA/PostgreSQL.
Dependencias solo hacia adentro: infrastructure → application → domain.

## Comandos
```bash
./mvnw verify            # formato + pruebas + cobertura (obligatorio antes de terminar)
./mvnw test              # solo pruebas
./mvnw spotless:apply    # aplicar formato
./mvnw spring-boot:run   # correr (necesita PostgreSQL: DB_URL, DB_USER, DB_PASSWORD)
docker build -t ms-transfer-limits .
```
JDK 27 y Maven locales en `~/tools` (no tocan el JAVA_HOME del sistema): los scripts de `.claude/hooks/env.sh` lo resuelven. En terminal manual: `source .claude/hooks/env.sh`.

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
