package com.lab.transferlimits.domain;

/** Resultado de validar una transferencia. */
public sealed interface ValidationResult {

  /** Disponible del dia del canal despues de la validacion. */
  Money availableToday();

  /** Aprobada: el monto ya quedo sumado al consumo. */
  record Approved(Money availableToday) implements ValidationResult {}

  /** Rechazada: no consumio limite. */
  record Rejected(RejectionReason reason, Money perOperationLimit, Money availableToday)
      implements ValidationResult {}
}
