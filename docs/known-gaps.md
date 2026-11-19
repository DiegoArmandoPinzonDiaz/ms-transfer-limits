# Known gaps

- **Atomicidad del SQL de consumo contra PostgreSQL real (AC-16).** `JdbcLimitsRepository.CONSUME`
  (INSERT ... ON CONFLICT DO UPDATE condicionado) se prueba solo con `JdbcClient` simulado, que
  valida el flujo y el mapeo pero no el comportamiento de bloqueo de PostgreSQL. Comprobarlo de
  verdad requiere una prueba de integracion con Testcontainers y transacciones concurrentes,
  omitida por requerimiento (sin pruebas de integracion). AC-16 a nivel de caso de uso si esta
  cubierto con un adaptador en memoria y 50 hilos.
- **Migraciones Flyway y arranque del contexto Spring** tampoco se ejercitan en pruebas por la misma
  razon; se verifican levantando `docker compose up --build`.
- **Autenticacion y autorizacion:** los endpoints no tienen autenticacion; cualquiera puede cambiar
  limites con el PUT. Fuera del alcance del ejercicio.
- **Actions e imagenes por tag, no por SHA/digest:** `.github/workflows/ci.yml` (checkout, setup-java,
  cache), `Dockerfile` (eclipse-temurin) y `docker-compose.yml` (postgres, jaeger) fijan versiones por
  tag. Para endurecer la cadena de suministro habria que fijarlas por SHA o digest.
- **AC-11 en la consulta:** hay prueba de que el dia de negocio es America/Mexico_City y de que la
  validacion reinicia el consumo a las 00:00 (`ValidateTransferServiceTest`), pero no de que la
  consulta de limites (GET) muestre consumo 0 despues de la medianoche.
- **Aislamiento de transaccion:** `consume` fija READ COMMITTED; si se cambiara el aislamiento a
  REPEATABLE READ o SERIALIZABLE aparecerian errores 40001 que hoy no se reintentan.
