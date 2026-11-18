package com.lab.transferlimits;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class TransferLimitsApplicationTest {

  @Test
  void applicationIsAnnotatedAsSpringBootApplication() {
    assertThat(TransferLimitsApplication.class).hasAnnotation(SpringBootApplication.class);
  }
}
