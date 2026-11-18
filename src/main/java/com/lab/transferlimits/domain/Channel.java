package com.lab.transferlimits.domain;

/** Canal de la transferencia. */
public enum Channel {
  MOBILE,
  WEB,
  BRANCH;

  /** Convierte el texto exacto del canal; cualquier otro valor es una peticion invalida. */
  public static Channel parse(String value) {
    for (Channel channel : values()) {
      if (channel.name().equals(value)) {
        return channel;
      }
    }
    throw new InvalidRequestException("Canal invalido: debe ser MOBILE, WEB o BRANCH.");
  }
}
