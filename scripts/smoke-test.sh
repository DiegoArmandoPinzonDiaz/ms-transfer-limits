#!/usr/bin/env bash
# Smoke test del servicio con la app y Jaeger arriba (docker compose up --build -d).
# Local y CI: BASE_URL (default http://localhost:8080), JAEGER_URL (default http://localhost:16686).
# Verifica los codigos HTTP esperados segun ACCEPTANCE-CRITERIA.md, el traceId en problem+json y
# las trazas en Jaeger (traceId coincide, cuenta enmascarada). Sale con 1 si algo no coincide.
set -u
BASE=${BASE_URL:-http://localhost:8080}
JAEGER=${JAEGER_URL:-http://localhost:16686}
ACCOUNT="SMK$(date +%s)${RANDOM}"   # cuenta nueva por corrida: no depende de datos previos
API="/api/v1/accounts/$ACCOUNT"
FAILS=0
CODE=""; OUT=""; CT=""

req() { # METHOD PATH [BODY]
  local tmp; tmp=$(mktemp)
  if [ $# -ge 3 ]; then
    CODE=$(curl -s -o "$tmp" -w '%{http_code}' -X "$1" -H 'Content-Type: application/json' -d "$3" "$BASE$2")
  else
    CODE=$(curl -s -o "$tmp" -w '%{http_code}' -X "$1" "$BASE$2")
  fi
  OUT=$(cat "$tmp"); rm -f "$tmp"
}

req_ct() { # igual que req pero guarda tambien el Content-Type
  local tmp; tmp=$(mktemp)
  local out
  out=$(curl -s -o "$tmp" -w '%{http_code} %{content_type}' -X "$1" -H 'Content-Type: application/json' -d "$3" "$BASE$2")
  CODE=${out%% *}; CT=${out#* }; OUT=$(cat "$tmp"); rm -f "$tmp"
}

fail() { echo "FAIL $1"; [ -n "${OUT:-}" ] && echo "     respuesta: ${OUT:0:300}"; FAILS=$((FAILS + 1)); }

check() { # descripcion codigo_esperado [regex en el cuerpo]
  if [ "$CODE" != "$2" ]; then fail "$1: esperaba $2, recibi $CODE"; return; fi
  if [ -n "${3:-}" ] && ! echo "$OUT" | grep -Eq "$3"; then fail "$1: el cuerpo no cumple /$3/"; return; fi
  echo "ok   $1 ($CODE)"
}

check_problem() { # descripcion codigo [regex]: ademas exige problem+json y traceId
  check "$1" "$2" "${3:-}" || true
  [ "$CODE" = "$2" ] || return
  case "$CT" in *application/problem+json*) ;; *) fail "$1: Content-Type '$CT' no es problem+json"; return;; esac
  echo "$OUT" | grep -Eq '"traceId":"[0-9a-f]{32}"' || fail "$1: falta traceId en el problem+json"
}

trace_of() { echo "$OUT" | grep -oE '"traceId":"[0-9a-f]{32}"' | head -1 | cut -d'"' -f4; }

# 0. Esperar a que la app este arriba
for _ in $(seq 1 "${HEALTH_RETRIES:-60}"); do
  curl -fs "$BASE/actuator/health" | grep -q '"status":"UP"' && break
  sleep 3
done
req GET /actuator/health; check "health" 200 '"status":"UP"'
[ "$CODE" = "200" ] || { echo "La app no responde en $BASE"; exit 1; }

# 1-2. Limites: crear (AC-12) y consultar (AC-15)
req PUT "$API/limits/MOBILE" '{"dailyLimit": 1000.00, "perOperationLimit": 300.00}'
check "AC-12 crear limites MOBILE" 200 '"channel":"MOBILE"'
req GET "$API/limits"
check "AC-15 consultar limites" 200 '"dailyLimit".*"perOperationLimit".*"consumedToday".*"availableToday"'

# 3. Aprobada (AC-01)
req POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 250.50}'
check "AC-01 transferencia aprobada" 200 '"availableToday":749\.5'

# 4. Rechazo por limite por operacion (AC-04)
req_ct POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 301.00}'
check_problem "AC-04 rechazo por limite por operacion" 422 'PER_OPERATION_LIMIT_EXCEEDED.*requestedAmount|requestedAmount.*PER_OPERATION_LIMIT_EXCEEDED'
TRACE_ID=$(trace_of)

# 5. Rechazo por acumulado (AC-05): 250.50 + 300 + 300 = 850.50; quedan 149.50
req POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 300.00}'; check "acumular 300 (1/2)" 200
req POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 300.00}'; check "acumular 300 (2/2)" 200
req_ct POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 300.00}'
check_problem "AC-05 rechazo por acumulado" 422 'DAILY_LIMIT_EXCEEDED'
echo "$OUT" | grep -Eq '"availableAmount":149\.5' || fail "AC-05: availableAmount esperado 149.50"

# 6. El rechazo no consume (AC-06) y otros canales no se afectan (AC-07)
req GET "$API/limits"; check "AC-06 rechazo no consume (consumido 850.50)" 200 '"consumedToday":850\.5'

# 7-8. Sin limites configurados (AC-10)
req_ct POST "$API/transfer-validations" '{"channel": "WEB", "amount": 10}'
check_problem "AC-10 canal sin limites" 404

# 9. Peticiones invalidas (AC-08, AC-09, AC-13)
req_ct POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 0}';      check_problem "AC-08 monto cero" 400
req_ct POST "$API/transfer-validations" '{"channel": "MOBILE", "amount": 10.001}'; check_problem "AC-08 mas de 2 decimales" 400
req_ct POST "$API/transfer-validations" '{"channel": "ATM", "amount": 10}';        check_problem "AC-09 canal inexistente" 400
req_ct PUT "$API/limits/ATM" '{"dailyLimit": 100, "perOperationLimit": 10}';       check_problem "AC-09 canal inexistente en PUT" 400
req_ct PUT "$API/limits/WEB" '{"dailyLimit": 100, "perOperationLimit": 101}';      check_problem "AC-13 por operacion mayor al diario" 400

# 10. Cuenta inexistente en consulta (AC-10) con traceId
OUT=$(curl -s -D /tmp/smoke-h.$$ "$BASE/api/v1/accounts/NO-EXISTE-$RANDOM/limits")
CODE=$(head -1 /tmp/smoke-h.$$ | awk '{print $2}'); CT=$(grep -i '^content-type' /tmp/smoke-h.$$ | tr -d '\r' | cut -d' ' -f2-); rm -f /tmp/smoke-h.$$
check_problem "AC-10 cuenta inexistente" 404
NOT_FOUND_TRACE=$(trace_of)

# 11. Jaeger (API JSON interna). Se reintenta ~30 s: el exportador OTLP envia por lotes.
echo "Esperando trazas en Jaeger ($JAEGER)..."
printf '%s\n%s\n' "$TRACE_ID" "${NOT_FOUND_TRACE:-}" > /tmp/smoke-last-trace-id   # para diagnostico en CI
JOUT=""
fail_j() { echo "FAIL $1"; echo "     Jaeger respondio: ${JOUT:0:600}"; FAILS=$((FAILS + 1)); }

TRACE_JSON=""
for _ in $(seq 1 15); do
  [ -n "$TRACE_ID" ] && TRACE_JSON=$(curl -s -m 10 "$JAEGER/api/traces/$TRACE_ID")
  if echo "$TRACE_JSON" | grep -q '"operationName":"transfer.validate"'; then break; fi
  sleep 2
done
ACTUAL=$(echo "$TRACE_JSON" | grep -oE '"serviceName":"[^"]*"' | sort -u | tr '\n' ' ')
echo "info servicio(s) dentro de la traza $TRACE_ID: ${ACTUAL:-<ninguno>}"

JOUT=$TRACE_JSON
if echo "$ACTUAL" | grep -qF '"serviceName":"ms-transfer-limits"'; then echo "ok   el servicio de la traza es ms-transfer-limits"
else fail_j "el servicio de la traza no es ms-transfer-limits (servicio en la traza: ${ACTUAL:-ninguno})"; fi

JOUT=$TRACE_JSON
if echo "$TRACE_JSON" | grep -q "\"traceID\":\"$TRACE_ID\""; then echo "ok   el traceId del problem+json ($TRACE_ID) existe en Jaeger"
else fail_j "Jaeger no tiene el traceId $TRACE_ID del problem+json"; fi

if echo "$TRACE_JSON" | grep -q '"operationName":"transfer.validate"'; then echo "ok   span propio transfer.validate presente en esa traza"
else fail_j "la traza $TRACE_ID no tiene el span transfer.validate"; fi

SPANS=$(echo "$TRACE_JSON" | grep -o '"spanID"' | wc -l)
if [ "$SPANS" -ge 2 ]; then echo "ok   la traza tiene $SPANS spans (peticion HTTP + transfer.validate)"
else fail_j "la traza solo tiene $SPANS span(s): transfer.validate no es hijo de la peticion HTTP"; fi

if echo "$TRACE_JSON" | grep -qF '"transfer.account_masked"' && echo "$TRACE_JSON" | grep -qF "\"****${ACCOUNT: -4}\""; then
  echo "ok   accountId enmascarado en el span (****${ACCOUNT: -4})"
else fail_j "no se ve la cuenta enmascarada ****${ACCOUNT: -4} en la traza"; fi

if echo "$TRACE_JSON" | grep -qF "$ACCOUNT"; then fail_j "el accountId COMPLETO aparece en la traza"
else echo "ok   el accountId completo no aparece en la traza"; fi

# API v3 de Jaeger 2.x (listado de servicios y busqueda): solo informativa, nunca hace fallar el smoke test
JOUT=$(curl -s -m 10 "$JAEGER/api/v3/services")
if echo "$JOUT" | grep -q 'ms-transfer-limits'; then echo "ok   /api/v3/services lista ms-transfer-limits"
else echo "WARN /api/v3/services no lista ms-transfer-limits: ${JOUT:0:200}"; fi
T0=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)
T1=$(date -u -d '5 minutes' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+5M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)
JOUT=$(curl -s -m 10 "$JAEGER/api/v3/traces?query.service_name=ms-transfer-limits&query.start_time_min=$T0&query.start_time_max=$T1&query.num_traces=20")
if echo "$JOUT" | grep -qi "$TRACE_ID"; then echo "ok   /api/v3/traces devuelve la traza"
else echo "WARN /api/v3/traces no devolvio la traza: ${JOUT:0:200}"; fi

echo
if [ "$FAILS" -ne 0 ]; then echo "SMOKE TEST FALLO: $FAILS verificaciones"; exit 1; fi
echo "SMOKE TEST OK"
