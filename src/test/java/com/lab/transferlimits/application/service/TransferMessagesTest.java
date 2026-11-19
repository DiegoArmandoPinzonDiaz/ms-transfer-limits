package com.lab.transferlimits.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferMessagesTest {

  @Test
  @DisplayName("AC-01: mensaje de aprobacion con el disponible restante")
  void ac01ApprovedMessage() {
    String message =
        TransferMessages.forResult(
            new Approved(Money.of(new BigDecimal("900"))), Money.of(new BigDecimal("100")));

    assertThat(message).isEqualTo("Transferencia aprobada. Disponible del dia: 900.00.");
  }

  @Test
  @DisplayName("Técnica: seguridad de plantillas: un valor con sintaxis de variable no se evalua")
  void valuesAreNeverEvaluatedAsTemplates() {
    String message =
        TransferMessages.render(
            "Monto ${requested}", Map.of("requested", "${sys:user.name} ${script:js:1+1}"));

    assertThat(message).isEqualTo("Monto ${sys:user.name} ${script:js:1+1}");
  }
}
