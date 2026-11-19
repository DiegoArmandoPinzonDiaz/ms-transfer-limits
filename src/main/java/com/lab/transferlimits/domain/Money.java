package com.lab.transferlimits.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monto no negativo con exactamente 2 decimales. */
public final class Money {

  private static final int SCALE = 2;

  /**
   * Tope: 17 digitos enteros + 2 decimales caben en NUMERIC(19,2) y no permiten enteros gigantes.
   */
  private static final int MAX_INTEGER_DIGITS = 17;

  public static final Money ZERO =
      new Money(BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY));

  private final BigDecimal value;

  private Money(BigDecimal value) {
    this.value = value;
  }

  /** Monto mayor o igual a 0 con maximo 2 decimales. */
  public static Money of(BigDecimal raw) {
    return create(raw, true);
  }

  /** Monto mayor a 0 con maximo 2 decimales. */
  public static Money positive(BigDecimal raw) {
    return create(raw, false);
  }

  private static Money create(BigDecimal raw, boolean allowZero) {
    if (raw == null
        || raw.signum() < 0
        || (!allowZero && raw.signum() == 0)
        // Antes de setScale: 1e999999999 construiria un entero enorme y tumbaria el servicio.
        || (long) raw.precision() - raw.scale() > MAX_INTEGER_DIGITS
        || raw.stripTrailingZeros().scale() > SCALE) {
      throw new InvalidRequestException(
          allowZero
              ? "El monto debe ser mayor o igual a 0 con maximo 2 decimales."
              : "El monto debe ser mayor a 0 con maximo 2 decimales.");
    }
    return new Money(raw.setScale(SCALE, RoundingMode.UNNECESSARY));
  }

  public BigDecimal value() {
    return value;
  }

  public Money plus(Money other) {
    return new Money(value.add(other.value));
  }

  /** Resta; si el resultado seria negativo devuelve 0. */
  public Money minusFloorZero(Money other) {
    BigDecimal result = value.subtract(other.value);
    return result.signum() < 0 ? ZERO : new Money(result);
  }

  public boolean isGreaterThan(Money other) {
    return value.compareTo(other.value) > 0;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Money other && value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toPlainString();
  }
}
