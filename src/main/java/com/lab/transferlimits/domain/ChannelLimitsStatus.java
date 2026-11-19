package com.lab.transferlimits.domain;

import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;

/** Limites de un canal junto con lo consumido en el dia. */
public record ChannelLimitsStatus(ChannelLimits limits, Money consumedToday) {

  /** Disponible del dia; 0 si el consumo ya alcanzo o supero el limite diario. */
  public Money availableToday() {
    return limits.dailyLimit().minusFloorZero(consumedToday);
  }

  /** Evalua un monto sin modificar el estado. Primero el limite por operacion, luego el diario. */
  public ValidationResult evaluate(Money amount) {
    Money available = availableToday();
    if (amount.isGreaterThan(limits.perOperationLimit())) {
      return new Rejected(
          RejectionReason.PER_OPERATION_LIMIT_EXCEEDED, limits.perOperationLimit(), available);
    }
    if (amount.isGreaterThan(available)) {
      return new Rejected(
          RejectionReason.DAILY_LIMIT_EXCEEDED, limits.perOperationLimit(), available);
    }
    return new Approved(available.minusFloorZero(amount));
  }
}
