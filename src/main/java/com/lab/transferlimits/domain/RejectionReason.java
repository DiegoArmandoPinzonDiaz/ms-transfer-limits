package com.lab.transferlimits.domain;

/** Motivo por el que se rechaza una transferencia. */
public enum RejectionReason {
  PER_OPERATION_LIMIT_EXCEEDED,
  DAILY_LIMIT_EXCEEDED
}
