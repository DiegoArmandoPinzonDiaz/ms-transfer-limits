package com.lab.transferlimits.application.service;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.ValidationResult;
import java.time.Clock;

public class ValidateTransferService implements ValidateTransferUseCase {

  private final LimitsRepositoryPort repository;
  private final Clock clock;

  public ValidateTransferService(LimitsRepositoryPort repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public Result validate(Command command) {
    ValidationResult outcome =
        repository
            .consume(
                command.accountId(), command.channel(), BusinessDay.today(clock), command.amount())
            .orElseThrow(LimitsNotFoundException::new);
    return new Result(outcome, TransferMessages.forResult(outcome, command.amount()));
  }
}
