package com.lab.transferlimits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelLimitsTest {

  private static ChannelLimits limits(String daily, String perOperation) {
    return ChannelLimits.of(Channel.WEB, new BigDecimal(daily), new BigDecimal(perOperation));
  }

  @Test
  @DisplayName("AC-13: limites validos; por operacion igual al diario es aceptado")
  void ac13ValidLimits() {
    assertThat(limits("1000", "300").perOperationLimit())
        .isEqualTo(Money.of(new BigDecimal("300")));
    assertThat(limits("500", "500").dailyLimit()).isEqualTo(Money.of(new BigDecimal("500")));
  }

  @Test
  @DisplayName("AC-13: limites en cero o negativos son peticion invalida")
  void ac13NonPositiveLimits() {
    assertThatThrownBy(() -> limits("0", "0")).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> limits("1000", "0")).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> limits("-1", "-1")).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("AC-13: limite por operacion mayor que el diario es peticion invalida")
  void ac13PerOperationGreaterThanDaily() {
    assertThatThrownBy(() -> limits("100", "100.01")).isInstanceOf(InvalidRequestException.class);
  }
}
