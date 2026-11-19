package com.lab.transferlimits.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Command;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Result;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryResourceAttributes;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Lo que realmente se exporta: el service.name sale de la configuracion real (application.yml) con
 * la misma clase que usa Spring Boot, y el span transfer.validate es hijo del span actual.
 */
class ExportedSpanTest {

  private static String serviceNameFromRealConfig() throws IOException {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));
    StandardEnvironment environment = new StandardEnvironment();
    sources.forEach(source -> environment.getPropertySources().addLast(source));
    Map<String, String> attributes = new HashMap<>();
    new OpenTelemetryResourceAttributes(environment, Map.of()).applyTo(attributes::put);
    return attributes.get("service.name");
  }

  @Test
  @DisplayName("Traza: service.name del recurso es exactamente ms-transfer-limits")
  void serviceNameIsApplicationName() throws Exception {
    assertThat(serviceNameFromRealConfig()).isEqualTo("ms-transfer-limits");
  }

  @Test
  @DisplayName(
      "Traza: el span exportado tiene service.name, nombre, atributos y es hijo de la peticion")
  void exportedSpanIsNamedMaskedAndChildOfCurrentSpan() throws Exception {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    Resource resource =
        Resource.create(
            io.opentelemetry.api.common.Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("service.name"),
                serviceNameFromRealConfig()));
    try (OpenTelemetrySdk telemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build()) {
      ValidateTransferUseCase delegate = mock(ValidateTransferUseCase.class);
      when(delegate.validate(any()))
          .thenReturn(new Result(new Approved(Money.of(BigDecimal.TEN)), "ok"));
      TracedValidateTransferUseCase traced = new TracedValidateTransferUseCase(delegate, telemetry);
      Span request = telemetry.getTracer("http").spanBuilder("http post").startSpan();

      try (Scope ignored = request.makeCurrent()) {
        traced.validate(new Command("ACC-00004684", Channel.WEB, Money.positive(BigDecimal.ONE)));
      } finally {
        request.end();
      }

      List<SpanData> spans = exporter.getFinishedSpanItems();
      SpanData child =
          spans.stream()
              .filter(s -> s.getName().equals("transfer.validate"))
              .findFirst()
              .orElseThrow();
      SpanData parent =
          spans.stream().filter(s -> s.getName().equals("http post")).findFirst().orElseThrow();
      assertThat(
              child
                  .getResource()
                  .getAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("service.name")))
          .isEqualTo("ms-transfer-limits");
      assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
      assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
      assertThat(child.getAttributes().get(TracedValidateTransferUseCase.ACCOUNT))
          .isEqualTo("****4684");
      assertThat(child.getAttributes().toString()).doesNotContain("ACC-00004684");
    }
  }
}
