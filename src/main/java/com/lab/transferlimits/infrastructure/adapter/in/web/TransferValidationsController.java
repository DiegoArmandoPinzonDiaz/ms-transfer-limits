package com.lab.transferlimits.infrastructure.adapter.in.web;

import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import com.lab.transferlimits.infrastructure.adapter.in.web.api.TransferValidationsApi;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.TransferApproval;
import com.lab.transferlimits.infrastructure.adapter.in.web.model.TransferValidationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador REST de validacion de transferencias. Un rechazo se traduce a 422 problem+json. */
@RestController
public class TransferValidationsController implements TransferValidationsApi {

  private final ValidateTransferUseCase validateTransfer;

  public TransferValidationsController(ValidateTransferUseCase validateTransfer) {
    this.validateTransfer = validateTransfer;
  }

  @Override
  public ResponseEntity<TransferApproval> validateTransfer(
      String accountId, TransferValidationRequest request) {
    Money amount = Money.positive(request.getAmount());
    var result =
        validateTransfer.validate(
            new ValidateTransferUseCase.Command(
                accountId, Channel.parse(request.getChannel()), amount));
    if (result.outcome() instanceof Rejected rejected) {
      throw new TransferRejectedException(rejected, amount, result.message());
    }
    return ResponseEntity.ok(
        new TransferApproval(result.message(), result.outcome().availableToday().value()));
  }
}
