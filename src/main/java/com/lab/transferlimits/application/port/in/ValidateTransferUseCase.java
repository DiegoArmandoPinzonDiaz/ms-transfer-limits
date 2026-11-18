package com.lab.transferlimits.application.port.in;

import com.lab.transferlimits.domain.ValidationResult;
import java.math.BigDecimal;

/** Validar una transferencia y registrar su consumo si se aprueba. */
public interface ValidateTransferUseCase {

  record Command(String accountId, String channel, BigDecimal amount) {}

  /** Resultado del dominio y mensaje listo para el cliente. */
  record Result(ValidationResult outcome, String message) {}

  /**
   * @throws com.lab.transferlimits.domain.InvalidRequestException monto o canal invalido.
   * @throws com.lab.transferlimits.application.LimitsNotFoundException sin limites configurados.
   */
  Result validate(Command command);
}
