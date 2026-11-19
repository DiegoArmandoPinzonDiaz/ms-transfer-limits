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

# 11. Jaeger: servicio, traceId del problem+json y cuenta enmascarada (esperar al exportador por lotes)
echo "Esperando trazas en Jaeger ($JAEGER)..."
FOUND_SERVICE=0; FOUND_TRACE=0
for _ in $(seq 1 30); do
  curl -fs "$JAEGER/api/services" | grep -q 'ms-transfer-limits' && FOUND_SERVICE=1
  [ -n "$TRACE_ID" ] && curl -fs "$JAEGER/api/traces/$TRACE_ID" | grep -q "\"traceID\":\"$TRACE_ID\"" && FOUND_TRACE=1
  [ "$FOUND_SERVICE" = 1 ] && [ "$FOUND_TRACE" = 1 ] && break
  sleep 3
done
[ "$FOUND_SERVICE" = 1 ] && echo "ok   Jaeger tiene trazas del servicio ms-transfer-limits" || fail "Jaeger no tiene el servicio ms-transfer-limits"
[ "$FOUND_TRACE" = 1 ] && echo "ok   el traceId del problem+json ($TRACE_ID) existe en Jaeger" || fail "Jaeger no tiene el traceId $TRACE_ID del problem+json"

TRACES=$(curl -fs "$JAEGER/api/traces?service=ms-transfer-limits&limit=100")
echo "$TRACES" | grep -q 'transfer.validate' && echo "ok   span propio transfer.validate presente" || fail "no hay span transfer.validate"
echo "$TRACES" | grep -q "\"\\*\\*\\*\\*${ACCOUNT: -4}\"" && echo "ok   accountId enmascarado en el span (****${ACCOUNT: -4})" || fail "no se ve la cuenta enmascarada ****${ACCOUNT: -4}"
if echo "$TRACES" | grep -q "$ACCOUNT"; then fail "el accountId COMPLETO aparece en las trazas de Jaeger"; else echo "ok   el accountId completo no aparece en las trazas"; fi

echo
if [ "$FAILS" -ne 0 ]; then echo "SMOKE TEST FALLO: $FAILS verificaciones"; exit 1; fi
echo "SMOKE TEST OK"
