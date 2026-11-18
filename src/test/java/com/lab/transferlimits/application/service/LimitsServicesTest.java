package com.lab.transferlimits.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.transferlimits.application.InMemoryLimitsRepository;
import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimitsServicesTest {

  private static final String ACCOUNT = "ACC-1";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC);

  private UpdateLimitService update;
  private GetLimitsService get;
  private ValidateTransferService validate;

  @BeforeEach
  void setUp() {
    InMemoryLimitsRepository repository = new InMemoryLimitsRepository();
    update = new UpdateLimitService(repository, CLOCK);
    get = new GetLimitsService(repository, CLOCK);
    validate = new ValidateTransferService(repository, CLOCK);
  }

  private ChannelLimitsStatus put(String channel, String daily, String perOperation) {
    return update.updateLimit(
        new UpdateLimitUseCase.Command(
            ACCOUNT,
            ChannelLimits.of(
                Channel.parse(channel), new BigDecimal(daily), new BigDecimal(perOperation))));
  }

  private void spend(String channel, String amount) {
    validate.validate(
        new ValidateTransferUseCase.Command(
            ACCOUNT, Channel.parse(channel), Money.positive(new BigDecimal(amount))));
  }

  private static Money money(String value) {
    return Money.of(new BigDecimal(value));
  }

  @Test
  @DisplayName("AC-12: si el canal no tenia limites, se crean")
  void ac12CreatesLimits() {
    ChannelLimitsStatus status = put("WEB", "2000", "500");

    assertThat(status.limits().dailyLimit()).isEqualTo(money("2000"));
    assertThat(status.limits().perOperationLimit()).isEqualTo(money("500"));
    assertThat(status.consumedToday()).isEqualTo(Money.ZERO);
  }

  @Test
  @DisplayName("AC-12: actualiza limite diario y por operacion de un canal con limites")
  void ac12UpdatesLimits() {
    put("WEB", "2000", "500");

    ChannelLimitsStatus status = put("WEB", "3000", "800");

    assertThat(status.limits().dailyLimit()).isEqualTo(money("3000"));
    assertThat(status.limits().perOperationLimit()).isEqualTo(money("800"));
    assertThat(get.getLimits(ACCOUNT)).hasSize(1);
  }

  @Test
  @DisplayName("AC-10: sin limites configurados la consulta responde no encontrado")
  void ac10NothingSavedMeansNotFound() {
    assertThatThrownBy(() -> get.getLimits(ACCOUNT)).isInstanceOf(LimitsNotFoundException.class);
  }

  @Test
  @DisplayName(
      "AC-14: limite diario menor a lo consumido se acepta y rechaza nuevas transferencias")
  void ac14LowerDailyLimitThanConsumed() {
    put("WEB", "1000", "500");
    spend("WEB", "500");
    spend("WEB", "500");

    ChannelLimitsStatus status = put("WEB", "600", "100");

    assertThat(status.consumedToday()).isEqualTo(money("1000"));
    assertThat(status.availableToday()).isEqualTo(Money.ZERO);
    var result =
        validate.validate(
            new ValidateTransferUseCase.Command(
                ACCOUNT, Channel.WEB, Money.positive(BigDecimal.ONE)));
    assertThat(((Rejected) result.outcome()).reason())
        .isEqualTo(RejectionReason.DAILY_LIMIT_EXCEEDED);
  }

  @Test
  @DisplayName("AC-15: la consulta devuelve por canal limites, consumido hoy y disponible hoy")
  void ac15QueryPerChannel() {
    put("MOBILE", "5000", "1000");
    put("WEB", "2000", "500");
    spend("MOBILE", "700");

    List<ChannelLimitsStatus> statuses = get.getLimits(ACCOUNT);

    assertThat(statuses).hasSize(2);
    ChannelLimitsStatus mobile = statuses.getFirst();
    assertThat(mobile.limits().channel()).isEqualTo(Channel.MOBILE);
    assertThat(mobile.limits().dailyLimit()).isEqualTo(money("5000"));
    assertThat(mobile.limits().perOperationLimit()).isEqualTo(money("1000"));
    assertThat(mobile.consumedToday()).isEqualTo(money("700"));
    assertThat(mobile.availableToday()).isEqualTo(money("4300"));
    assertThat(statuses.get(1).consumedToday()).isEqualTo(Money.ZERO);
  }

  @Test
  @DisplayName("AC-10: consultar una cuenta sin limites responde no encontrado")
  void ac10QueryWithoutLimits() {
    assertThatThrownBy(() -> get.getLimits("SIN-LIMITES"))
        .isInstanceOf(LimitsNotFoundException.class);
  }
}
