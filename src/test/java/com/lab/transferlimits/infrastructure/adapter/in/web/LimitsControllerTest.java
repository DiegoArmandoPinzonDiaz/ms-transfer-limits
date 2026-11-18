package com.lab.transferlimits.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LimitsControllerTest {

  private static final String LIMITS = "/api/v1/accounts/ACC-1/limits";
  private static final String PROBLEM = "application/problem+json";

  private final GetLimitsUseCase getLimits = mock(GetLimitsUseCase.class);
  private final UpdateLimitUseCase updateLimit = mock(UpdateLimitUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new LimitsController(getLimits, updateLimit))
            .setControllerAdvice(new ProblemExceptionHandler())
            .build();
  }

  private static ChannelLimitsStatus limitsStatus(
      Channel channel, String daily, String perOp, String used) {
    return new ChannelLimitsStatus(
        ChannelLimits.of(channel, new BigDecimal(daily), new BigDecimal(perOp)),
        Money.of(new BigDecimal(used)));
  }

  @Test
  @DisplayName("AC-15: GET devuelve por canal limite diario, por operacion, consumido y disponible")
  void ac15QueryReturnsPerChannel() throws Exception {
    when(getLimits.getLimits("ACC-1"))
        .thenReturn(List.of(limitsStatus(Channel.MOBILE, "5000", "1000", "1500")));

    mvc.perform(get(LIMITS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("ACC-1"))
        .andExpect(jsonPath("$.channels[0].channel").value("MOBILE"))
        .andExpect(jsonPath("$.channels[0].dailyLimit").value(5000.00))
        .andExpect(jsonPath("$.channels[0].perOperationLimit").value(1000.00))
        .andExpect(jsonPath("$.channels[0].consumedToday").value(1500.00))
        .andExpect(jsonPath("$.channels[0].availableToday").value(3500.00));
  }

  @Test
  @DisplayName("AC-10 y AC-17: cuenta sin limites responde 404 problem+json")
  void ac10NotFoundIsProblemJson() throws Exception {
    when(getLimits.getLimits("ACC-1")).thenThrow(new LimitsNotFoundException());

    mvc.perform(get(LIMITS))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(
            jsonPath("$.type")
                .value("https://transferlimits.lab.example/problems/limits-not-found"))
        .andExpect(jsonPath("$.title").value("Limites no encontrados"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  @DisplayName("AC-12: PUT crea o actualiza los limites del canal y responde 200")
  void ac12PutCreatesOrUpdates() throws Exception {
    when(updateLimit.updateLimit(any())).thenReturn(limitsStatus(Channel.WEB, "2000", "500", "0"));

    mvc.perform(
            put(LIMITS + "/WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyLimit\": 2000.00, \"perOperationLimit\": 500.00}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.channel").value("WEB"))
        .andExpect(jsonPath("$.dailyLimit").value(2000.00))
        .andExpect(jsonPath("$.availableToday").value(2000.00));

    ArgumentCaptor<UpdateLimitUseCase.Command> captor =
        ArgumentCaptor.forClass(UpdateLimitUseCase.Command.class);
    verify(updateLimit).updateLimit(captor.capture());
    assertThat(captor.getValue().accountId()).isEqualTo("ACC-1");
    assertThat(captor.getValue().limits().channel()).isEqualTo(Channel.WEB);
    assertThat(captor.getValue().limits().perOperationLimit())
        .isEqualTo(Money.of(new BigDecimal("500")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"dailyLimit\": 0, \"perOperationLimit\": 0}",
        "{\"dailyLimit\": -5, \"perOperationLimit\": 1}",
        "{\"dailyLimit\": 100, \"perOperationLimit\": 100.01}",
        "{\"dailyLimit\": 100.001, \"perOperationLimit\": 10}"
      })
  @DisplayName(
      "AC-13: limites en cero, negativos, con mas de 2 decimales o por operacion > diario son 400")
  void ac13InvalidLimitsAre400(String body) throws Exception {
    mvc.perform(put(LIMITS + "/WEB").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(updateLimit);
  }

  @Test
  @DisplayName("AC-09: canal inexistente en la ruta es 400 problem+json")
  void ac09UnknownChannelIs400() throws Exception {
    mvc.perform(
            put(LIMITS + "/ATM")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyLimit\": 100, \"perOperationLimit\": 10}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(
            jsonPath("$.type")
                .value("https://transferlimits.lab.example/problems/invalid-request"));
    verifyNoInteractions(updateLimit);
  }

  @Test
  @DisplayName("AC-17: faltan campos obligatorios o el JSON es invalido responde 400 problem+json")
  void ac17MalformedBodiesAre400() throws Exception {
    mvc.perform(put(LIMITS + "/WEB").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dailyLimit")));
    mvc.perform(put(LIMITS + "/WEB").contentType(MediaType.APPLICATION_JSON).content("{no-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.detail").value("El cuerpo de la peticion no es valido."));
  }

  @Test
  @DisplayName("AC-17: metodo no soportado responde problem+json")
  void ac17MethodNotAllowedIsProblemJson() throws Exception {
    mvc.perform(delete(LIMITS))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM));
  }

  @Test
  @DisplayName("AC-17: un error inesperado responde 500 generico sin SQL, clases ni stack trace")
  void ac17UnexpectedErrorDoesNotLeakDetails() throws Exception {
    when(getLimits.getLimits("ACC-1"))
        .thenThrow(
            new IllegalStateException(
                "org.postgresql.util.PSQLException: ERROR: relation channel_limits SELECT * FROM x"));

    String body =
        mvc.perform(get(LIMITS))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(PROBLEM))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("Ocurrio un error inesperado."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("postgresql", "PSQL", "SELECT", "Exception", "com.lab", "channel_limits");
  }
}
