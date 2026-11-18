package com.lab.transferlimits.application.port.in;

import com.lab.transferlimits.domain.ChannelLimitsStatus;
import java.math.BigDecimal;

/** Crear o actualizar los limites de un canal. */
public interface UpdateLimitUseCase {

  record Command(
      String accountId, String channel, BigDecimal dailyLimit, BigDecimal perOperationLimit) {}

  /**
   * @throws com.lab.transferlimits.domain.InvalidRequestException canal o limites invalidos.
   */
  ChannelLimitsStatus updateLimit(Command command);
}
