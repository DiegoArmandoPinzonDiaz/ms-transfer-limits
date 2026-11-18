# Criterios de aceptación - MS-TRANSFER-LIMITS
Definidos por el desarrollador. Cada prueba debe referenciar el ID del criterio que cubre.

## Validación de transferencias
- AC-01: Si el monto no supera el límite por operación ni lo disponible del día del canal, se aprueba y el monto se suma al consumo del día.
- AC-02: Si el monto es exactamente igual al límite por operación, se aprueba.
- AC-03: Si el monto es exactamente igual a lo disponible del día, se aprueba y el disponible queda en 0.
- AC-04: Si el monto supera el límite por operación, se rechaza con un mensaje que indica el límite por operación y el monto solicitado.
- AC-05: Si el monto supera lo disponible del día, se rechaza con un mensaje que indica el disponible y el monto solicitado.
- AC-06: Una transferencia rechazada no consume límite.
- AC-07: El consumo de un canal no afecta a los otros canales de la misma cuenta.
- AC-08: Montos en cero, negativos o con más de 2 decimales se rechazan como petición inválida (400).
- AC-09: Canal inexistente (distinto de MOBILE, WEB, BRANCH) se rechaza como petición inválida (400).
- AC-10: Si la cuenta o el canal no tiene límites configurados, se responde 404. No existen límites por defecto.

## Día y reinicio
- AC-11: El día se calcula en la zona horaria America/Mexico_City. El consumo se reinicia a las 00:00 de esa zona.

## Actualización de límites
- AC-12: Se puede actualizar el límite diario y el límite por operación de un canal. Si el canal no tenía límites, se crean.
- AC-13: Ambos límites deben ser mayores a 0 y el límite por operación no puede ser mayor que el diario; si no, 400.
- AC-14: Si el nuevo límite diario es menor a lo ya consumido hoy, la actualización se acepta y las nuevas transferencias del día se rechazan por falta de disponible.

## Consulta
- AC-15: La consulta de límites devuelve por cada canal: límite diario, límite por operación, consumido hoy y disponible hoy.

## Concurrencia
- AC-16: Si llegan validaciones simultáneas para la misma cuenta y canal, la suma de las aprobadas nunca supera el límite diario.

## Errores
- AC-17: Todos los errores se responden en formato RFC 9457 (application/problem+json) sin exponer detalles internos (stack traces, SQL, nombres de clases).
