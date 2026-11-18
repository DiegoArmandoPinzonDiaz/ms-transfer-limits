package com.lab.transferlimits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChannelTest {

  @Test
  @DisplayName("AC-09: MOBILE, WEB y BRANCH son canales validos")
  void ac09ValidChannels() {
    assertThat(Channel.parse("MOBILE")).isEqualTo(Channel.MOBILE);
    assertThat(Channel.parse("WEB")).isEqualTo(Channel.WEB);
    assertThat(Channel.parse("BRANCH")).isEqualTo(Channel.BRANCH);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"ATM", "web", ""})
  @DisplayName("AC-09: canal distinto de MOBILE, WEB, BRANCH es peticion invalida")
  void ac09InvalidChannel(String value) {
    assertThatThrownBy(() -> Channel.parse(value)).isInstanceOf(InvalidRequestException.class);
  }
}
