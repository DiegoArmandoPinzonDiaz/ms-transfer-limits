package com.lab.transferlimits.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lab.transferlimits.application.BusinessDay;
import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UseCaseConfigTest {

  private final UseCaseConfig config = new UseCaseConfig();

  @Test
  @DisplayName("AC-11: el Clock inyectado usa America/Mexico_City")
  void ac11ClockUsesMexicoCity() {
    assertThat(config.clock().getZone()).isEqualTo(BusinessDay.ZONE);
  }

  @Test
  @DisplayName("Cableado: los casos de uso se crean con el repositorio y el reloj")
  void wiresUseCases() {
    LimitsRepositoryPort repository = mock(LimitsRepositoryPort.class);
    Clock clock = config.clock();

    assertThat(config.getLimitsUseCase(repository, clock)).isNotNull();
    assertThat(config.updateLimitUseCase(repository, clock)).isNotNull();
    assertThat(config.validateTransferUseCase(repository, clock, OpenTelemetry.noop())).isNotNull();
  }
}
