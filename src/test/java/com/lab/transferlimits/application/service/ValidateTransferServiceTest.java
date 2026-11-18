package com.lab.transferlimits.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.transferlimits.application.InMemoryLimitsRepository;
import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Command;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Result;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.InvalidRequestException;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidateTransferServiceTest {

  private static final String ACCOUNT = "ACC-1";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

  private InMemoryLimitsRepository repository;
  private ValidateTransferService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryLimitsRepository();
    service = new ValidateTransferService(repository, CLOCK);
    UpdateLimitService update = new UpdateLimitService(repository, CLOCK);
    // Diario 1000, por operacion 300 en MOBILE y WEB.
    for (String channel : new String[] {"MOBILE", "WEB"}) {
      update.updateLimit(
          new UpdateLimitUseCase.Command(
              ACCOUNT, channel, new BigDecimal("1000"), new BigDecimal("300")));
    }
  }

  private Result validate(String channel, String amount) {
    return service.validate(new Command(ACCOUNT, channel, new BigDecimal(amount)));
  }

  private Money consumed(Channel channel) {
    return repository.findAll(ACCOUNT, TODAY).stream()
        .filter(s -> s.limits().channel() == channel)
        .findFirst()
        .orElseThrow()
        .consumedToday();
  }

  private static Money money(String value) {
    return Money.of(new BigDecimal(value));
  }

  @Test
  @DisplayName("AC-01: dentro de los limites se aprueba y el monto se suma al consumo del dia")
  void ac01ApprovedAndConsumed() {
    Result result = validate("MOBILE", "100");

    assertThat(result.outcome()).isEqualTo(new Approved(money("900")));
    assertThat(consumed(Channel.MOBILE)).isEqualTo(money("100"));
  }

  @Test
  @DisplayName("AC-02: monto igual al limite por operacion se aprueba")
  void ac02EqualToPerOperationLimit() {
    assertThat(validate("MOBILE", "300").outcome()).isInstanceOf(Approved.class);
  }

  @Test
  @DisplayName("AC-03: monto igual al disponible se aprueba y el disponible queda en 0")
  void ac03EqualToAvailable() {
    validate("MOBILE", "300");
    validate("MOBILE", "300");
    validate("MOBILE", "300");

    Result result = validate("MOBILE", "100");

    assertThat(result.outcome()).isEqualTo(new Approved(Money.ZERO));
  }

  @Test
  @DisplayName("AC-04: sobre el limite por operacion se rechaza indicando limite y monto")
  void ac04PerOperationMessage() {
    Result result = validate("MOBILE", "301");

    assertThat(((Rejected) result.outcome()).reason())
        .isEqualTo(RejectionReason.PER_OPERATION_LIMIT_EXCEEDED);
    assertThat(result.message())
        .isEqualTo("El monto solicitado 301.00 excede el limite por operacion de 300.00.");
  }

  @Test
  @DisplayName("AC-05: sobre el disponible del dia se rechaza indicando disponible y monto")
  void ac05DailyMessage() {
    validate("MOBILE", "300");
    validate("MOBILE", "300");
    validate("MOBILE", "300");

    Result result = validate("MOBILE", "150");

    assertThat(((Rejected) result.outcome()).reason())
        .isEqualTo(RejectionReason.DAILY_LIMIT_EXCEEDED);
    assertThat(result.message())
        .isEqualTo("El monto solicitado 150.00 excede el disponible del dia de 100.00.");
  }

  @Test
  @DisplayName("AC-06: una transferencia rechazada no consume limite")
  void ac06RejectedDoesNotConsume() {
    validate("MOBILE", "300");
    validate("MOBILE", "301");

    assertThat(consumed(Channel.MOBILE)).isEqualTo(money("300"));
  }

  @Test
  @DisplayName("AC-07: el consumo de un canal no afecta a los otros canales")
  void ac07ChannelsAreIndependent() {
    validate("MOBILE", "300");

    assertThat(consumed(Channel.WEB)).isEqualTo(Money.ZERO);
    assertThat(validate("WEB", "300").outcome()).isEqualTo(new Approved(money("700")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-10", "10.001"})
  @DisplayName("AC-08: monto en cero, negativo o con mas de 2 decimales es peticion invalida")
  void ac08InvalidAmount(String amount) {
    assertThatThrownBy(() -> validate("MOBILE", amount))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(consumed(Channel.MOBILE)).isEqualTo(Money.ZERO);
  }

  @Test
  @DisplayName("AC-09: canal inexistente es peticion invalida")
  void ac09InvalidChannel() {
    assertThatThrownBy(() -> validate("ATM", "10")).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @DisplayName("AC-10: cuenta o canal sin limites configurados responde no encontrado")
  void ac10NoLimitsConfigured() {
    assertThatThrownBy(() -> service.validate(new Command("OTRA", "MOBILE", new BigDecimal("10"))))
        .isInstanceOf(LimitsNotFoundException.class);
    assertThatThrownBy(() -> validate("BRANCH", "10")).isInstanceOf(LimitsNotFoundException.class);
  }

  @Test
  @DisplayName(
      "AC-11: el dia es America/Mexico_City y el consumo se reinicia a las 00:00 de esa zona")
  void ac11MexicoCityDayAndReset() {
    // 05:59:59Z = 23:59:59 del 9 de marzo en Ciudad de Mexico (UTC-6); 06:00:00Z = 00:00 del 10.
    Clock beforeMidnight = Clock.fixed(Instant.parse("2026-03-10T05:59:59Z"), ZoneOffset.UTC);
    Clock afterMidnight = Clock.fixed(Instant.parse("2026-03-10T06:00:00Z"), ZoneOffset.UTC);
    ValidateTransferService before = new ValidateTransferService(repository, beforeMidnight);
    ValidateTransferService after = new ValidateTransferService(repository, afterMidnight);
    Command command = new Command(ACCOUNT, "MOBILE", new BigDecimal("300"));

    before.validate(command);
    before.validate(command);
    before.validate(command);

    assertThat(repository.findAll(ACCOUNT, LocalDate.of(2026, 3, 9)).getFirst().consumedToday())
        .isEqualTo(money("900"));
    assertThat(after.validate(command).outcome()).isEqualTo(new Approved(money("700")));
    assertThat(repository.findAll(ACCOUNT, LocalDate.of(2026, 3, 10)).getFirst().consumedToday())
        .isEqualTo(money("300"));
  }
}
