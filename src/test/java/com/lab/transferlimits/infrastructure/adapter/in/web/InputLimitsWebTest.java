package com.lab.transferlimits.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import jakarta.validation.Validation;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class InputLimitsWebTest {

  private static final String LONG_ACCOUNT =
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String PROBLEM = "application/problem+json";

  private final GetLimitsUseCase getLimits = mock(GetLimitsUseCase.class);
  private final UpdateLimitUseCase updateLimit = mock(UpdateLimitUseCase.class);
  private final ValidateTransferUseCase validate = mock(ValidateTransferUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                validated(new LimitsController(getLimits, updateLimit)),
                validated(new TransferValidationsController(validate)))
            .setControllerAdvice(new ProblemExceptionHandler())
            .build();
  }

  /** Replica la validacion por proxy (@Validated) que aplica Spring Boot a los controllers. */
  private static Object validated(Object controller) {
    ProxyFactory factory = new ProxyFactory(controller);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new MethodValidationInterceptor(Validation.buildDefaultValidatorFactory().getValidator()));
    return factory.getProxy();
  }

  @Test
  @Timeout(5)
  @DisplayName(
      "AC-08: un monto gigante (1e999999999) responde 400 problem+json, sin tumbar el servicio")
  void ac08HugeAmountIs400() throws Exception {
    mvc.perform(
            post("/api/v1/accounts/ACC-1/transfer-validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\": \"MOBILE\", \"amount\": 1e999999999}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    verifyNoInteractions(validate);
  }

  @Test
  @Timeout(5)
  @DisplayName("AC-13: limites gigantes en el PUT responden 400 problem+json (no 500 por NUMERIC)")
  void ac13HugeLimitsAre400() throws Exception {
    mvc.perform(
            put("/api/v1/accounts/ACC-1/limits/WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"dailyLimit\": 1e999999999, \"perOperationLimit\": 100000000000000000}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    verifyNoInteractions(updateLimit);
  }

  @ParameterizedTest
  @ValueSource(strings = {LONG_ACCOUNT, "bad%20id", "ACC%2F1", "ACC%3B1"})
  @DisplayName(
      "AC-17: un accountId demasiado largo o con caracteres no permitidos responde 400, no 500")
  void accountIdIsValidated(String accountId) throws Exception {
    mvc.perform(get("/api/v1/accounts/" + accountId + "/limits"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM))
        .andExpect(jsonPath("$.status").value(400));
    verifyNoInteractions(getLimits);
  }

  @Test
  @DisplayName("AC-17: un accountId invalido tambien se valida en el PUT y en la validacion")
  void accountIdIsValidatedEverywhere() throws Exception {
    mvc.perform(
            put("/api/v1/accounts/" + LONG_ACCOUNT + "/limits/WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dailyLimit\": 100, \"perOperationLimit\": 10}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/accounts/" + LONG_ACCOUNT + "/transfer-validations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\": \"WEB\", \"amount\": 10}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(updateLimit, validate);
  }

  @Test
  @DisplayName(
      "AC-17: un error de datos responde 500 generico y el log solo lleva clase y SQLState")
  void dataErrorLogsOnlyClassAndSqlState() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(ProblemExceptionHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      when(getLimits.getLimits("ACC-1"))
          .thenThrow(
              new DuplicateKeyException(
                  "Key (account_id)=(ACC-SECRET-9876) already exists",
                  new SQLException("duplicate key value ACC-SECRET-9876", "23505")));

      String body =
          mvc.perform(get("/api/v1/accounts/ACC-1/limits"))
              .andExpect(status().isInternalServerError())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(body).doesNotContain("ACC-SECRET").doesNotContain("23505");
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getFormattedMessage()).contains("23505").contains("java.sql.SQLException");
      assertThat(event.getFormattedMessage()).doesNotContain("ACC-SECRET");
      assertThat(event.getThrowableProxy()).isNull();
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  @DisplayName("AC-17: la validacion integrada de Spring tambien responde 400 problem+json")
  void builtInMethodValidationIs400() {
    var response =
        new ProblemExceptionHandler()
            .handleHandlerMethodValidationException(
                mock(HandlerMethodValidationException.class),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                mock(WebRequest.class));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
  }
}
