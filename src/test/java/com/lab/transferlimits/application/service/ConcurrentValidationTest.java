package com.lab.transferlimits.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.transferlimits.application.InMemoryLimitsRepository;
import com.lab.transferlimits.application.port.in.UpdateLimitUseCase;
import com.lab.transferlimits.application.port.in.ValidateTransferUseCase;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConcurrentValidationTest {

  @Test
  @DisplayName("AC-16: validaciones simultaneas nunca aprueban mas que el limite diario")
  void ac16ConcurrentValidationsNeverExceedDailyLimit() throws Exception {
    Clock clock = Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC);
    InMemoryLimitsRepository repository = new InMemoryLimitsRepository();
    new UpdateLimitService(repository, clock)
        .updateLimit(
            new UpdateLimitUseCase.Command(
                "ACC-1", "MOBILE", new BigDecimal("1000"), new BigDecimal("100")));
    ValidateTransferService service = new ValidateTransferService(repository, clock);
    int threads = 50;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return service
                          .validate(
                              new ValidateTransferUseCase.Command(
                                  "ACC-1", "MOBILE", new BigDecimal("100")))
                          .outcome()
                      instanceof Approved;
                }));
      }
      start.countDown();
      int approved = 0;
      for (Future<Boolean> future : futures) {
        if (future.get()) {
          approved++;
        }
      }

      assertThat(approved).isEqualTo(10);
      assertThat(repository.findAll("ACC-1", LocalDate.of(2026, 3, 10)).getFirst().consumedToday())
          .isEqualTo(Money.of(new BigDecimal("1000")));
    } finally {
      pool.shutdownNow();
    }
  }
}
