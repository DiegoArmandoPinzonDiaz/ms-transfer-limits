package com.lab.transferlimits.application;

/** La cuenta o el canal no tiene limites configurados. Se traduce a 404. */
public class LimitsNotFoundException extends RuntimeException {

  public LimitsNotFoundException() {
    super("No hay limites configurados para la cuenta y canal indicados.");
  }
}
