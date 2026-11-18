package com.lab.transferlimits.application.service;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import java.time.Clock;
import java.util.List;

public class GetLimitsService implements GetLimitsUseCase {

  private final LimitsRepositoryPort repository;
  private final Clock clock;

  public GetLimitsService(LimitsRepositoryPort repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public List<ChannelLimitsStatus> getLimits(String accountId) {
    List<ChannelLimitsStatus> statuses = repository.findAll(accountId, BusinessDay.today(clock));
    if (statuses.isEmpty()) {
      throw new LimitsNotFoundException();
    }
    return statuses;
  }
}
