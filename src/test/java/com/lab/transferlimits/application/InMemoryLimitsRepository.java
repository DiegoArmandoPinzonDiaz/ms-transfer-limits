package com.lab.transferlimits.application;

import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptador en memoria thread-safe, solo para pruebas. validar+sumar ocurre dentro del lock. */
public class InMemoryLimitsRepository implements LimitsRepositoryPort {

  private final Map<String, ChannelLimits> limits = new HashMap<>();
  private final Map<String, Money> consumed = new HashMap<>();

  @Override
  public synchronized List<ChannelLimitsStatus> findAll(String accountId, LocalDate day) {
    List<ChannelLimitsStatus> result = new ArrayList<>();
    for (Channel channel : Channel.values()) {
      status(accountId, channel, day).ifPresent(result::add);
    }
    return result;
  }

  @Override
  public synchronized ChannelLimitsStatus save(
      String accountId, ChannelLimits channelLimits, LocalDate day) {
    limits.put(limitsKey(accountId, channelLimits.channel()), channelLimits);
    return status(accountId, channelLimits.channel(), day).orElseThrow();
  }

  @Override
  public synchronized Optional<ValidationResult> consume(
      String accountId, Channel channel, LocalDate day, Money amount) {
    Optional<ChannelLimitsStatus> current = status(accountId, channel, day);
    if (current.isEmpty()) {
      return Optional.empty();
    }
    ValidationResult result = current.get().evaluate(amount);
    if (result instanceof ValidationResult.Approved) {
      consumed.put(
          consumedKey(accountId, channel, day), current.get().consumedToday().plus(amount));
    }
    return Optional.of(result);
  }

  private Optional<ChannelLimitsStatus> status(String accountId, Channel channel, LocalDate day) {
    ChannelLimits channelLimits = limits.get(limitsKey(accountId, channel));
    if (channelLimits == null) {
      return Optional.empty();
    }
    Money used = consumed.getOrDefault(consumedKey(accountId, channel, day), Money.ZERO);
    return Optional.of(new ChannelLimitsStatus(channelLimits, used));
  }

  private static String limitsKey(String accountId, Channel channel) {
    return accountId + "|" + channel;
  }

  private static String consumedKey(String accountId, Channel channel, LocalDate day) {
    return limitsKey(accountId, channel) + "|" + day;
  }
}
