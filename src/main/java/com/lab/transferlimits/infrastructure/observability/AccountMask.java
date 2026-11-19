package com.lab.transferlimits.infrastructure.observability;

/** Enmascara identificadores de cuenta para atributos de trazas y logs. */
public final class AccountMask {

  private static final int VISIBLE = 4;
  private static final String MASK = "****";

  private AccountMask() {}

  /** Deja visibles solo los ultimos 4 caracteres; si hay 4 o menos, oculta todo. */
  public static String mask(String accountId) {
    if (accountId == null || accountId.length() <= VISIBLE) {
      return MASK;
    }
    return MASK + accountId.substring(accountId.length() - VISIBLE);
  }
}
