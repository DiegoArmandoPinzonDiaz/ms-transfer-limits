---
name: new-endpoint
description: Agregar un endpoint REST contract-first (openapi.yaml, implementación, pruebas desde ACCEPTANCE-CRITERIA.md, mvnw verify).
---

1. Lee `ACCEPTANCE-CRITERIA.md`. Si el endpoint no tiene criterios, pregunta; no inventes reglas.
2. Actualiza `openapi.yaml` primero: path, request/response, y errores como `application/problem+json`.
3. Implementa de adentro hacia afuera: dominio → caso de uso/puertos → adaptador web (controller delgado, sin lógica) → persistencia si aplica.
4. Pruebas unitarias (sin `@SpringBootTest`) derivadas de los criterios; ramas incluidas.
5. Errores vía skill `problem-details`.
6. `./mvnw verify` debe pasar (formato, pruebas, cobertura >= 90%).
