package com.lab.transferlimits.infrastructure.adapter.in.web;

import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.infrastructure.adapter.in.web.api.LimitsApi;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.AccountLimitsResponse;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.ChannelLimitsResponse;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.ChannelName;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.UpdateLimitsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador REST de limites: convierte formatos y delega en los casos de uso. */
@RestController
public class LimitsController implements LimitsApi {

  private final GetLimitsUseCase getLimits;
  private final UpdateLimitUseCase updateLimit;

  public LimitsController(GetLimitsUseCase getLimits, UpdateLimitUseCase updateLimit) {
    this.getLimits = getLimits;
    this.updateLimit = updateLimit;
  }

  @Override
  public ResponseEntity<AccountLimitsResponse> getAccountLimits(String accountId) {
    var channels =
        getLimits.getLimits(accountId).stream().map(LimitsController::toResponse).toList();
    return ResponseEntity.ok(new AccountLimitsResponse(accountId, channels));
  }

  @Override
  public ResponseEntity<ChannelLimitsResponse> updateChannelLimits(
      String accountId, String channelId, UpdateLimitsRequest request) {
    ChannelLimits limits =
        ChannelLimits.of(
            Channel.parse(channelId), request.getDailyLimit(), request.getPerOperationLimit());
    ChannelLimitsStatus status =
        updateLimit.updateLimit(new UpdateLimitUseCase.Command(accountId, limits));
    return ResponseEntity.ok(toResponse(status));
  }

  private static ChannelLimitsResponse toResponse(ChannelLimitsStatus status) {
    ChannelLimits limits = status.limits();
    return new ChannelLimitsResponse(
        ChannelName.fromValue(limits.channel().name()),
        limits.dailyLimit().value(),
        limits.perOperationLimit().value(),
        status.consumedToday().value(),
        status.availableToday().value());
  }
}
