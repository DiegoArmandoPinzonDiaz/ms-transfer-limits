package com.lab.transferlimits.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelLimitsStatusTest {

  private static Money money(String value) {
    return Money.of(new BigDecimal(value));
  }

  /** Diario 1000, por operacion 300, con el consumo indicado. */
  private static ChannelLimitsStatus status(String consumed) {
    ChannelLimits limits =
        ChannelLimits.of(Channel.MOBILE, new BigDecimal("1000"), new BigDecimal("300"));
    return new ChannelLimitsStatus(limits, money(consumed));
  }

  @Test
  @DisplayName("AC-01: monto dentro de ambos limites se aprueba y descuenta del disponible")
  void ac01Approved() {
    assertThat(status("0").evaluate(money("100"))).isEqualTo(new Approved(money("900")));
  }

  @Test
  @DisplayName("AC-02: monto igual al limite por operacion se aprueba")
  void ac02EqualToPerOperationLimit() {
    assertThat(status("0").evaluate(money("300"))).isInstanceOf(Approved.class);
  }

  @Test
  @DisplayName("AC-03: monto igual al disponible se aprueba y el disponible queda en 0")
  void ac03EqualToAvailable() {
    assertThat(status("900").evaluate(money("100"))).isEqualTo(new Approved(Money.ZERO));
  }

  @Test
  @DisplayName("AC-04: monto sobre el limite por operacion se rechaza")
  void ac04PerOperationExceeded() {
    assertThat(status("0").evaluate(money("300.01")))
        .isEqualTo(
            new Rejected(
                RejectionReason.PER_OPERATION_LIMIT_EXCEEDED, money("300"), money("1000")));
  }

  @Test
  @DisplayName("AC-05: monto sobre el disponible del dia se rechaza")
  void ac05DailyExceeded() {
    assertThat(status("900").evaluate(money("100.01")))
        .isEqualTo(new Rejected(RejectionReason.DAILY_LIMIT_EXCEEDED, money("300"), money("100")));
  }

  @Test
  @DisplayName("AC-04: si excede ambos limites, prevalece el limite por operacion")
  void ac04PerOperationCheckedFirst() {
    assertThat(((Rejected) status("900").evaluate(money("500"))).reason())
        .isEqualTo(RejectionReason.PER_OPERATION_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("AC-14: con consumo mayor al limite diario el disponible es 0 y se rechaza")
  void ac14ConsumedAboveDailyLimit() {
    ChannelLimitsStatus status = status("1200");
    assertThat(status.availableToday()).isEqualTo(Money.ZERO);
    assertThat(status.evaluate(money("1")))
        .isEqualTo(new Rejected(RejectionReason.DAILY_LIMIT_EXCEEDED, money("300"), Money.ZERO));
  }
}
