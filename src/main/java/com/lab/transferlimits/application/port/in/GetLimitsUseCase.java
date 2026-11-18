package com.lab.transferlimits.application.port.in;

import com.lab.transferlimits.domain.ChannelLimitsStatus;
import java.util.List;

/** Consultar los limites de una cuenta por canal. */
public interface GetLimitsUseCase {

  /**
   * @throws com.lab.transferlimits.application.LimitsNotFoundException si no hay limites.
   */
  List<ChannelLimitsStatus> getLimits(String accountId);
}
