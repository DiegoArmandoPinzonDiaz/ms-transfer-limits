package com.lab.transferlimits.application.port.in;

import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;

/** Crear o actualizar los limites de un canal. */
public interface UpdateLimitUseCase {

  record Command(String accountId, ChannelLimits limits) {}

  ChannelLimitsStatus updateLimit(Command command);
}
