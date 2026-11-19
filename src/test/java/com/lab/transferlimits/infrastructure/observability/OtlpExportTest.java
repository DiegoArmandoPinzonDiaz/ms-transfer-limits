package com.lab.transferlimits.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OtlpExportTest {

  @Test
  @DisplayName(
      "Técnica: trazas: el exportador OTLP envia por HTTP con el sender del JDK (sin OkHttp ni Kotlin)")
  void exportsSpansWithJdkSender() throws Exception {
    CompletableFuture<String> received = new CompletableFuture<>();
    HttpServer collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    collector.createContext(
        "/v1/traces",
        exchange -> {
          received.complete(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    collector.start();
    try {
      OtlpHttpSpanExporter exporter =
          OtlpHttpSpanExporter.builder()
              .setEndpoint("http://127.0.0.1:" + collector.getAddress().getPort() + "/v1/traces")
              .build();
      try (SdkTracerProvider provider =
          SdkTracerProvider.builder()
              .addSpanProcessor(SimpleSpanProcessor.create(exporter))
              .build()) {
        provider.get("test").spanBuilder("transfer.validate").startSpan().end();
        CompletableResultCode flushed = provider.forceFlush().join(5, TimeUnit.SECONDS);
        assertThat(flushed.isSuccess()).isTrue();
      }

      assertThat(received.get(5, TimeUnit.SECONDS)).isEqualTo("POST /v1/traces");
    } finally {
      collector.stop(0);
    }
  }

  @Test
  @DisplayName("Técnica: dependencias: OkHttp y kotlin-stdlib ya no estan en el classpath")
  void okHttpAndKotlinAreGone() {
    assertThatThrownBy(() -> Class.forName("okhttp3.OkHttpClient"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("kotlin.Unit"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  @DisplayName("Técnica: privacidad: los spans HTTP no llevan la URL real (accountId completo)")
  void httpUrlIsRemovedFromObservations() {
    Observation.Context context = new Observation.Context();
    context.addHighCardinalityKeyValue(
        io.micrometer.common.KeyValue.of("http.url", "/api/v1/accounts/ACC-123456/limits"));
    context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("other", "kept"));
    context.addLowCardinalityKeyValue(
        io.micrometer.common.KeyValue.of("uri", "/api/v1/accounts/{accountId}/limits"));

    new HttpUrlRedactionFilter().map(context);

    assertThat(context.getHighCardinalityKeyValues().stream().map(kv -> kv.getKey()))
        .containsExactly("other");
    assertThat(context.getLowCardinalityKeyValues().stream().map(kv -> kv.getKey()))
        .containsExactly("uri");
  }
}
