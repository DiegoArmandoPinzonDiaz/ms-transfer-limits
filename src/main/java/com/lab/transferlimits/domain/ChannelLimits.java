package com.lab.transferlimits.domain;

import java.math.BigDecimal;

/** Limites de un canal. Invariante: 0 < limite por operacion <= limite diario. */
public record ChannelLimits(Channel channel, Money dailyLimit, Money perOperationLimit) {

  public ChannelLimits {
    if (perOperationLimit.isGreaterThan(dailyLimit)) {
      throw new InvalidRequestException(
          "El limite por operacion no puede ser mayor que el limite diario.");
    }
  }

  /** Crea limites validando que ambos sean mayores a 0 y la relacion entre ellos. */
  public static ChannelLimits of(Channel channel, BigDecimal daily, BigDecimal perOperation) {
    return new ChannelLimits(channel, Money.positive(daily), Money.positive(perOperation));
  }
}
