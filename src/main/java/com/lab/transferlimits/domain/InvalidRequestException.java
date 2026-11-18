package com.lab.transferlimits.domain;

/**
 * Peticion invalida (monto, canal o limites). Se traduce a 400. El mensaje es seguro de exponer.
 */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
