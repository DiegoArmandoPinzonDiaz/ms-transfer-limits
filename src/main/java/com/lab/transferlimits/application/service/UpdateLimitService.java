package com.lab.transferlimits.application.service;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
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
    ChannelLimits limits =
        ChannelLimits.of(
            Channel.parse(command.channel()), command.dailyLimit(), command.perOperationLimit());
    return repository.save(command.accountId(), limits, BusinessDay.today(clock));
  }
}
