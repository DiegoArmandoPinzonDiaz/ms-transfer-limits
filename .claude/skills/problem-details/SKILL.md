---
name: problem-details
description: Crear errores RFC 9457 (type, title, status, detail) con plantillas de Commons Text de forma segura.
---

- Toda respuesta de error es `application/problem+json` con `type` (URI estable), `title`, `status`, `detail`; extensiones opcionales como propiedades (`ProblemDetail#setProperty`).
- Centraliza en un `@RestControllerAdvice` del adaptador web; el dominio lanza excepciones tipadas, nunca `ProblemDetail`.
- `detail` se arma con plantillas: `new StringSubstitutor(Map<String,String>)` sobre una plantilla constante (`"Excede el limite por operacion ${limit}"`).
- Prohibido `StringSubstitutor.createInterpolator()` y cualquier lookup (script, dns, url, file). Nunca uses entrada del usuario como plantilla: solo como valor del Map.
- No incluir datos sensibles ni stack traces en `detail`.
- Cada tipo de error se declara en `openapi.yaml` y tiene prueba unitaria.
