package com.lab.transferlimits.infrastructure.adapter.in.web;

import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult.Rejected;

/** Rechazo de negocio de una transferencia; se traduce a 422 problem+json. */
public class TransferRejectedException extends RuntimeException {

  private final transient Rejected rejected;
  private final transient Money requestedAmount;

  public TransferRejectedException(Rejected rejected, Money requestedAmount, String message) {
    super(message);
    this.rejected = rejected;
    this.requestedAmount = requestedAmount;
  }

  public Rejected rejected() {
    return rejected;
  }

  public Money requestedAmount() {
    return requestedAmount;
  }
}
