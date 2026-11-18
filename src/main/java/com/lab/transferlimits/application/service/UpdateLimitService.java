package com.lab.transferlimits.application.service;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import java.time.Clock;

public class UpdateLimitService implements UpdateLimitUseCase {

  private final LimitsRepositoryPort repository;
  private final Clock clock;

  public UpdateLimitService(LimitsRepositoryPort repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public ChannelLimitsStatus updateLimit(Command command) {
    return repository.save(command.accountId(), command.limits(), BusinessDay.today(clock));
  }
}
