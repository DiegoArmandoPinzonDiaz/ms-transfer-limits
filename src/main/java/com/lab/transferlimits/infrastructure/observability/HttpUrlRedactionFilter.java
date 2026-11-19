package com.lab.transferlimits.infrastructure.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Quita de los spans HTTP los atributos con la URL real (la ruta contiene el accountId completo).
 * La ruta con plantilla (/api/v1/accounts/{accountId}/...) se conserva en el atributo de baja
 * cardinalidad.
 */
@Component
public class HttpUrlRedactionFilter implements ObservationFilter {

  static final Set<String> URL_KEYS = Set.of("http.url", "url.full", "url.path");

  @Override
  public Observation.Context map(Observation.Context context) {
    URL_KEYS.forEach(context::removeHighCardinalityKeyValue);
    return context;
  }
}
