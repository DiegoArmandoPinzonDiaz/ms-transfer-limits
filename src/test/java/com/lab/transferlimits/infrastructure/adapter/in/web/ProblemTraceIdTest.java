package com.lab.transferlimits.infrastructure.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProblemTraceIdTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String URL = "/api/v1/accounts/ACC-1/limits";

  private final GetLimitsUseCase getLimits = mock(GetLimitsUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new LimitsController(getLimits, mock(UpdateLimitUseCase.class)))
            .setControllerAdvice(new ProblemExceptionHandler())
            .build();
  }

  private static Scope withTrace() {
    SpanContext context =
        SpanContext.create(
            TRACE_ID, "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault());
    return Span.wrap(context).makeCurrent();
  }

  @Test
  @DisplayName("AC-17: errores de negocio (404) incluyen el traceId actual")
  void businessErrorHasTraceId() throws Exception {
    when(getLimits.getLimits("ACC-1")).thenThrow(new LimitsNotFoundException());

    try (Scope ignored = withTrace()) {
      mvc.perform(get(URL))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }
  }

  @Test
  @DisplayName("AC-17: errores inesperados (500) incluyen el traceId sin detalles internos")
  void unexpectedErrorHasTraceId() throws Exception {
    when(getLimits.getLimits("ACC-1")).thenThrow(new IllegalStateException("interno"));

    try (Scope ignored = withTrace()) {
      mvc.perform(get(URL))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.traceId").value(TRACE_ID))
          .andExpect(jsonPath("$.detail").value("Ocurrio un error inesperado."));
    }
  }

  @Test
  @DisplayName("AC-17: errores estandar de Spring MVC (405) tambien incluyen el traceId")
  void standardErrorHasTraceId() throws Exception {
    try (Scope ignored = withTrace()) {
      mvc.perform(delete(URL))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }
  }

  @Test
  @DisplayName("AC-17: sin traza activa no se agrega traceId")
  void noTraceNoTraceId() throws Exception {
    when(getLimits.getLimits("ACC-1")).thenThrow(new LimitsNotFoundException());

    mvc.perform(get(URL)).andExpect(jsonPath("$.traceId").doesNotExist());
  }
}
