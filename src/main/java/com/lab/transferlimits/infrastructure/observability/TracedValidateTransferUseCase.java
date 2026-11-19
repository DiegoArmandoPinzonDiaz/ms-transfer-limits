package com.lab.transferlimits.infrastructure.observability;

import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Span propio de la validacion. Atributos: canal, resultado, reasonCode y cuenta enmascarada. Nunca
 * incluye montos ni el accountId completo, ni el mensaje de las excepciones.
 */
public class TracedValidateTransferUseCase implements ValidateTransferUseCase {

  static final String SPAN_NAME = "transfer.validate";
  static final AttributeKey<String> CHANNEL = AttributeKey.stringKey("transfer.channel");
  static final AttributeKey<String> RESULT = AttributeKey.stringKey("transfer.result");
  static final AttributeKey<String> REASON_CODE = AttributeKey.stringKey("transfer.reason_code");
  static final AttributeKey<String> ACCOUNT = AttributeKey.stringKey("transfer.account_masked");

  private final ValidateTransferUseCase delegate;
  private final Tracer tracer;

  public TracedValidateTransferUseCase(ValidateTransferUseCase delegate, OpenTelemetry telemetry) {
    this.delegate = delegate;
    this.tracer = telemetry.getTracer("ms-transfer-limits");
  }

  @Override
  public Result validate(Command command) {
    Span span =
        tracer
            .spanBuilder(SPAN_NAME)
            .setAttribute(CHANNEL, command.channel().name())
            .setAttribute(ACCOUNT, AccountMask.mask(command.accountId()))
            .startSpan();
    try (Scope ignored = span.makeCurrent()) {
      Result result = delegate.validate(command);
      if (result.outcome() instanceof Rejected rejected) {
        span.setAttribute(RESULT, "rejected");
        span.setAttribute(REASON_CODE, rejected.reason().name());
      } else {
        span.setAttribute(RESULT, "approved");
      }
      return result;
    } catch (RuntimeException e) {
      span.setStatus(StatusCode.ERROR);
      throw e;
    } finally {
      span.end();
    }
  }
}
