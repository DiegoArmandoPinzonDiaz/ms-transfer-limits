package com.lab.transferlimits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "10.001", "5.123"})
  @DisplayName("AC-08: monto en cero, negativo o con mas de 2 decimales es peticion invalida")
  void ac08RejectsInvalidAmounts(String raw) {
    assertThatThrownBy(() -> Money.positive(new BigDecimal(raw)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("AC-08: monto nulo es peticion invalida")
  void ac08RejectsNull() {
    assertThatThrownBy(() -> Money.positive(null)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> Money.of(null)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("AC-08: montos validos se normalizan a 2 decimales")
  void ac08AcceptsValidAmounts() {
    assertThat(Money.positive(new BigDecimal("10.1")).toString()).isEqualTo("10.10");
    assertThat(Money.positive(new BigDecimal("10.100")).toString()).isEqualTo("10.10");
    assertThat(Money.positive(new BigDecimal("7")).value()).isEqualByComparingTo("7.00");
  }

  @Test
  @DisplayName("AC-03: el disponible puede ser 0 y la resta nunca baja de 0")
  void ac03ZeroAllowedAndFloor() {
    assertThat(Money.of(BigDecimal.ZERO)).isEqualTo(Money.ZERO);
    assertThatThrownBy(() -> Money.of(new BigDecimal("-1")))
        .isInstanceOf(InvalidRequestException.class);
    Money five = Money.positive(new BigDecimal("5"));
    Money three = Money.positive(new BigDecimal("3"));
    assertThat(five.minusFloorZero(three)).isEqualTo(Money.positive(new BigDecimal("2")));
    assertThat(five.minusFloorZero(five)).isEqualTo(Money.ZERO);
    assertThat(three.minusFloorZero(five)).isEqualTo(Money.ZERO);
  }

  @Test
  @DisplayName("AC-01: suma y comparacion de montos")
  void ac01PlusAndCompare() {
    Money five = Money.positive(new BigDecimal("5"));
    Money three = Money.positive(new BigDecimal("3"));
    assertThat(five.plus(three)).isEqualTo(Money.positive(new BigDecimal("8")));
    assertThat(five.isGreaterThan(three)).isTrue();
    assertThat(three.isGreaterThan(five)).isFalse();
    assertThat(five.compareTo(three)).isPositive();
    assertThat(five).hasSameHashCodeAs(Money.positive(new BigDecimal("5.00")));
    assertThat(five).isNotEqualTo("5.00");
  }
}
