package com.lab.transferlimits.infrastructure.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.transferlimits.application.LimitsNotFoundException;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase.Result;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransferValidationsControllerTest {

  private static final String URL = "/api/v1/accounts/ACC-1/transfer-validations";
  private static final String PROBLEM = "application/problem+json";

  private final ValidateTransferUseCase useCase = mock(ValidateTransferUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new TransferValidationsController(useCase))
            .setControllerAdvice(new ProblemExceptionHandler())
            .build();
  }

  private static Money money(String value) {
    return Money.of(new BigDecimal(value));
  }

  private ResultActions send(String json) throws Exception {
    return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  @Test
  @DisplayName("AC-01: transferencia aprobada responde 200 con mensaje y disponible")
  void ac01ApprovedIs200() throws Exception {
    when(useCase.validate(any()))
        .thenReturn(new Result(new Approved(money("3249.50")), "Transferencia aprobada."));

    send("{\"channel\": \"MOBILE\", \"amount\": 250.50}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Transferencia aprobada."))
        .andExpect(jsonPath("$.availableToday").value(3249.50));
  }

  @Test
  @DisplayName(
      "AC-04: rechazo por limite por operacion responde 422 problem+json con perOperationLimit")
  void ac04PerOperationRejectedIs422() throws Exception {
    String message = "El monto solicitado 1200.00 excede el limite por operacion de 1000.00.";
    when(useCase.validate(any()))
        .thenReturn(
            new Result(
                new Rejected(
                    RejectionReason.PER_OPERATION_LIMIT_EXCEEDED, money("1000"), money("3500")),
                message));

    send("{\"channel\": \"MOBILE\", \"amount\": 1200}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(
            jsonPath("$.type")
                .value("https://transferlimits.lab.example/problems/transfer-rejected"))
        .andExpect(jsonPath("$.title").value("Transferencia rechazada"))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.detail").value(message))
        .andExpect(jsonPath("$.reasonCode").value("PER_OPERATION_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.requestedAmount").value(1200.00))
        .andExpect(jsonPath("$.perOperationLimit").value(1000.00))
        .andExpect(jsonPath("$.availableAmount").doesNotExist());
  }

  @Test
  @DisplayName(
      "AC-05: rechazo por disponible del dia responde 422 problem+json con availableAmount")
  void ac05DailyRejectedIs422() throws Exception {
    String message = "El monto solicitado 800.00 excede el disponible del dia de 500.00.";
    when(useCase.validate(any()))
        .thenReturn(
            new Result(
                new Rejected(RejectionReason.DAILY_LIMIT_EXCEEDED, money("1000"), money("500")),
                message));

    send("{\"channel\": \"WEB\", \"amount\": 800}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.detail").value(message))
        .andExpect(jsonPath("$.reasonCode").value("DAILY_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.requestedAmount").value(800.00))
        .andExpect(jsonPath("$.availableAmount").value(500.00))
        .andExpect(jsonPath("$.perOperationLimit").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-10", "10.001"})
  @DisplayName("AC-08: monto en cero, negativo o con mas de 2 decimales responde 400 problem+json")
  void ac08InvalidAmountIs400(String amount) throws Exception {
    send("{\"channel\": \"MOBILE\", \"amount\": " + amount + "}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(useCase);
  }

  @Test
  @DisplayName("AC-09: canal inexistente responde 400 problem+json")
  void ac09UnknownChannelIs400() throws Exception {
    send("{\"channel\": \"ATM\", \"amount\": 10}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(
            jsonPath("$.type")
                .value("https://transferlimits.lab.example/problems/invalid-request"));
    verifyNoInteractions(useCase);
  }

  @Test
  @DisplayName("AC-10: cuenta o canal sin limites responde 404 problem+json")
  void ac10NoLimitsIs404() throws Exception {
    when(useCase.validate(any())).thenThrow(new LimitsNotFoundException());

    send("{\"channel\": \"BRANCH\", \"amount\": 10}")
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("AC-17: faltan campos obligatorios responde 400 problem+json")
  void ac17MissingFieldsIs400() throws Exception {
    send("{}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(useCase);
  }
}
