package com.lab.transferlimits.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Dia de negocio: siempre en America/Mexico_City, sin importar la zona del Clock inyectado. */
public final class BusinessDay {

  public static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

  private BusinessDay() {}

  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }
}
