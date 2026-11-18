package com.lab.transferlimits.application.service;

import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;

/**
 * Mensajes de validacion con plantillas fijas de Commons Text. Solo StringSubstitutor con un Map:
 * sin interpoladores ni lookups, y los valores nunca se evaluan como plantilla.
 */
final class TransferMessages {

  static final String APPROVED = "Transferencia aprobada. Disponible del dia: ${available}.";

  private static final Map<RejectionReason, String> REJECTIONS =
      Map.of(
          RejectionReason.PER_OPERATION_LIMIT_EXCEEDED,
          "El monto solicitado ${requested} excede el limite por operacion de ${limit}.",
          RejectionReason.DAILY_LIMIT_EXCEEDED,
          "El monto solicitado ${requested} excede el disponible del dia de ${available}.");

  private TransferMessages() {}

  static String forResult(ValidationResult result, Money requested) {
    Map<String, String> values = new HashMap<>();
    values.put("requested", requested.toString());
    values.put("available", result.availableToday().toString());
    String template = APPROVED;
    if (result instanceof Rejected rejected) {
      values.put("limit", rejected.perOperationLimit().toString());
      template = REJECTIONS.get(rejected.reason());
    }
    return render(template, values);
  }

  static String render(String template, Map<String, String> values) {
    StringSubstitutor substitutor = new StringSubstitutor(values);
    substitutor.setDisableSubstitutionInValues(true);
    return substitutor.replace(template);
  }
}
