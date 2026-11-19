package com.lab.transferlimits.infrastructure.config;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.port.in.GetLimitsUseCase;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.application.service.GetLimitsService;
import com.lab.transferlimits.application.service.UpdateLimitService;
import com.lab.transferlimits.application.service.ValidateTransferService;
import com.lab.transferlimits.infrastructure.observability.TracedValidateTransferUseCase;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cableado de casos de uso (la capa de aplicacion no depende de Spring). */
@Configuration
public class UseCaseConfig {

  @Bean
  Clock clock() {
    return Clock.system(BusinessDay.ZONE);
  }

  @Bean
  GetLimitsUseCase getLimitsUseCase(LimitsRepositoryPort repository, Clock clock) {
    return new GetLimitsService(repository, clock);
  }

  @Bean
  UpdateLimitUseCase updateLimitUseCase(LimitsRepositoryPort repository, Clock clock) {
    return new UpdateLimitService(repository, clock);
  }

  @Bean
  ValidateTransferUseCase validateTransferUseCase(
      LimitsRepositoryPort repository, Clock clock, OpenTelemetry openTelemetry) {
    return new TracedValidateTransferUseCase(
        new ValidateTransferService(repository, clock), openTelemetry);
  }
}
