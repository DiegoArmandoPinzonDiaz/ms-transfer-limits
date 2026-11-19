package com.lab.transferlimits.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Command;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Result;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TracedValidateTransferUseCaseTest {

  private static final String ACCOUNT = "ACC-00012345";

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final OpenTelemetrySdk telemetry =
      OpenTelemetrySdk.builder()
          .setTracerProvider(
              SdkTracerProvider.builder()
                  .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                  .build())
          .build();
  private final ValidateTransferUseCase delegate = mock(ValidateTransferUseCase.class);
  private final TracedValidateTransferUseCase traced =
      new TracedValidateTransferUseCase(delegate, telemetry);

  @AfterEach
  void tearDown() {
    telemetry.close();
  }

  private static Money money(String value) {
    return Money.of(new BigDecimal(value));
  }

  private static Command command() {
    return new Command(ACCOUNT, Channel.MOBILE, money("250.50"));
  }

  private SpanData onlySpan() {
    assertThat(exporter.getFinishedSpanItems()).hasSize(1);
    return exporter.getFinishedSpanItems().getFirst();
  }

  @Test
  @DisplayName("Técnica: traza: aprobada registra canal, resultado approved y cuenta enmascarada")
  void approvedSpan() {
    when(delegate.validate(any())).thenReturn(new Result(new Approved(money("10")), "ok"));

    traced.validate(command());

    SpanData span = onlySpan();
    assertThat(span.getName()).isEqualTo("transfer.validate");
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.CHANNEL)).isEqualTo("MOBILE");
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.RESULT))
        .isEqualTo("approved");
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.REASON_CODE)).isNull();
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.ACCOUNT))
        .isEqualTo("****2345");
  }

  @Test
  @DisplayName("Técnica: traza: rechazada registra resultado rejected y reasonCode")
  void rejectedSpan() {
    Rejected rejected =
        new Rejected(RejectionReason.DAILY_LIMIT_EXCEEDED, money("300"), money("100"));
    when(delegate.validate(any())).thenReturn(new Result(rejected, "no"));

    traced.validate(command());

    SpanData span = onlySpan();
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.RESULT))
        .isEqualTo("rejected");
    assertThat(span.getAttributes().get(TracedValidateTransferUseCase.REASON_CODE))
        .isEqualTo("DAILY_LIMIT_EXCEEDED");
  }

  @Test
  @DisplayName("Técnica: traza: nunca incluye el accountId completo ni montos")
  void spanHasNoSensitiveData() {
    when(delegate.validate(any())).thenReturn(new Result(new Approved(money("10")), "ok"));

    traced.validate(command());

    String attributes = onlySpan().getAttributes().toString();
    assertThat(attributes).doesNotContain(ACCOUNT).doesNotContain("250").doesNotContain("amount");
  }

  @Test
  @DisplayName("Técnica: traza: una excepcion marca el span con ERROR sin copiar el mensaje")
  void errorSpan() {
    when(delegate.validate(any())).thenThrow(new LimitsNotFoundException());

    assertThatThrownBy(() -> traced.validate(command()))
        .isInstanceOf(LimitsNotFoundException.class);

    SpanData span = onlySpan();
    assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(span.getEvents()).isEmpty();
  }

  @Test
  @DisplayName("Técnica: máscara de cuenta: solo los ultimos 4 caracteres")
  void accountMask() {
    assertThat(AccountMask.mask("ACC-00012345")).isEqualTo("****2345");
    assertThat(AccountMask.mask("1234")).isEqualTo("****");
    assertThat(AccountMask.mask("12")).isEqualTo("****");
    assertThat(AccountMask.mask(null)).isEqualTo("****");
  }
}
