package com.lab.transferlimits.application.port.out;

import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de salida de limites y consumo. */
public interface LimitsRepositoryPort {

  /** Estado de todos los canales configurados de la cuenta para el dia; vacio si no hay ninguno. */
  List<ChannelLimitsStatus> findAll(String accountId, LocalDate day);

  /** Crea o reemplaza los limites del canal conservando el consumo del dia. */
  ChannelLimitsStatus save(String accountId, ChannelLimits limits, LocalDate day);

  /**
   * Valida y consume en UN solo paso atomico: si {@link ChannelLimitsStatus#evaluate} aprueba, suma
   * el monto al consumo del dia; si rechaza, no consume. Dos llamadas concurrentes nunca pueden
   * aprobar mas que el limite diario. Vacio si el canal no tiene limites configurados.
   */
  Optional<ValidationResult> consume(
      String accountId, Channel channel, LocalDate day, Money amount);
}
