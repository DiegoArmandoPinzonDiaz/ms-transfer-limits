# Known gaps

- **Atomicidad del SQL de consumo contra PostgreSQL real (AC-16).** `JdbcLimitsRepository.CONSUME`
  (INSERT ... ON CONFLICT DO UPDATE condicionado) se prueba solo con `JdbcClient` simulado, que
  valida el flujo y el mapeo pero no el comportamiento de bloqueo de PostgreSQL. Comprobarlo de
  verdad requiere una prueba de integracion con Testcontainers y transacciones concurrentes,
  omitida por requerimiento (sin pruebas de integracion). AC-16 a nivel de caso de uso si esta
  cubierto con un adaptador en memoria y 50 hilos.
- **Migraciones Flyway y arranque del contexto Spring** tampoco se ejercitan en pruebas por la misma
  razon; se verifican levantando `docker compose up --build`.
- **Sin autenticacion/autorizacion** en los endpoints (fuera del alcance del ejercicio).
