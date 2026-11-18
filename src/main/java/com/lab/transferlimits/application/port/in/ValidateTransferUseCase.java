package com.lab.transferlimits.application.port.in;

import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult;

/** Validar una transferencia y registrar su consumo si se aprueba. */
public interface ValidateTransferUseCase {

  record Command(String accountId, Channel channel, Money amount) {}

  /** Resultado del dominio y mensaje listo para el cliente. */
  record Result(ValidationResult outcome, String message) {}

  /**
   * @throws com.lab.transferlimits.application.LimitsNotFoundException sin limites configurados.
   */
  Result validate(Command command);
}
