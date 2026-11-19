package com.lab.transferlimits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyCapTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"1e999999999", "1E+10000000", "100000000000000000", "123456789012345678.50"})
  @Timeout(value = 2)
  @DisplayName("AC-08: un monto gigante (mas de 17 digitos enteros) es peticion invalida y rapida")
  void ac08HugeAmountsAreRejectedFast(String raw) {
    assertThatThrownBy(() -> Money.positive(new BigDecimal(raw)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("AC-08: el monto maximo permitido (17 enteros + 2 decimales) se acepta")
  void ac08MaximumAmountIsAccepted() {
    assertThat(Money.positive(new BigDecimal("99999999999999999.99")).toString())
        .isEqualTo("99999999999999999.99");
  }

  @Test
  @DisplayName("AC-13: limites gigantes son peticion invalida (no desbordan NUMERIC)")
  void ac13HugeLimitsAreRejected() {
    assertThatThrownBy(
            () ->
                ChannelLimits.of(
                    Channel.WEB, new BigDecimal("1e999999999"), new BigDecimal("1000")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () ->
                ChannelLimits.of(
                    Channel.WEB, new BigDecimal("100000000000000000"), new BigDecimal("1")))
        .isInstanceOf(InvalidRequestException.class);
  }
}
