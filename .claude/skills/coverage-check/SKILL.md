---
name: coverage-check
description: Correr JaCoCo y listar las clases con cobertura bajo 90%.
---

1. `./mvnw verify` (genera `target/site/jacoco/jacoco.csv`; si falla por cobertura, igual queda el reporte).
2. Lista las clases bajo 90% de líneas o ramas:
```bash
awk -F, 'NR>1{ l=$8+$9; b=$6+$7; lc=(l? $9/l:1); bc=(b? $7/b:1);
  if (lc<0.9||bc<0.9) printf "%s.%s líneas=%.0f%% ramas=%.0f%%\n",$2,$3,lc*100,bc*100 }' target/site/jacoco/jacoco.csv
```
3. Reporta solo esa lista y qué ramas/líneas faltan. Las pruebas nuevas salen de `ACCEPTANCE-CRITERIA.md`; si falta un criterio, pregunta.
