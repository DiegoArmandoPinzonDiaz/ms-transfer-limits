package com.lab.transferlimits.infrastructure.adapter.out.persistence;

import com.lab.transferlimits.application.port.out.LimitsRepositoryPort;
import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.ValidationResult;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador PostgreSQL de limites y consumo diario. */
@Repository
public class JdbcLimitsRepository implements LimitsRepositoryPort {

  private static final String SELECT_STATUS =
      """
      SELECT l.channel, l.daily_limit, l.per_operation_limit, COALESCE(c.consumed, 0) AS consumed
        FROM channel_limits l
        LEFT JOIN daily_consumption c
          ON c.account_id = l.account_id AND c.channel = l.channel AND c.business_date = :day
       WHERE l.account_id = :account
      """;

  private static final String UPSERT_LIMITS =
      """
      INSERT INTO channel_limits (account_id, channel, daily_limit, per_operation_limit)
      VALUES (:account, :channel, :daily, :perOperation)
      ON CONFLICT (account_id, channel) DO UPDATE
         SET daily_limit = EXCLUDED.daily_limit,
             per_operation_limit = EXCLUDED.per_operation_limit
      """;

  /**
   * Validar y sumar en UNA sola sentencia. Es seguro ante concurrencia porque INSERT ... ON
   * CONFLICT DO UPDATE bloquea la fila (cuenta, canal, dia): una segunda sentencia espera a que la
   * primera confirme y reevalua el WHERE con el consumo ya actualizado, asi que la suma nunca
   * supera el limite diario. Si el WHERE no se cumple no escribe nada ni devuelve fila.
   */
  static final String CONSUME =
      """
      INSERT INTO daily_consumption (account_id, channel, business_date, consumed)
      SELECT l.account_id, l.channel, CAST(:day AS DATE), CAST(:amount AS NUMERIC)
        FROM channel_limits l
       WHERE l.account_id = :account AND l.channel = :channel
         AND CAST(:amount AS NUMERIC) <= l.per_operation_limit
         AND CAST(:amount AS NUMERIC) <= l.daily_limit
      ON CONFLICT (account_id, channel, business_date) DO UPDATE
         SET consumed = daily_consumption.consumed + EXCLUDED.consumed
       WHERE daily_consumption.consumed + EXCLUDED.consumed <=
             (SELECT l.daily_limit FROM channel_limits l
               WHERE l.account_id = EXCLUDED.account_id AND l.channel = EXCLUDED.channel)
      RETURNING consumed,
                (SELECT l.daily_limit FROM channel_limits l
                  WHERE l.account_id = :account AND l.channel = :channel) AS daily_limit
      """;

  private static final int MAX_ATTEMPTS = 3;

  private final JdbcClient jdbc;

  public JdbcLimitsRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ChannelLimitsStatus> findAll(String accountId, LocalDate day) {
    return jdbc
        .sql(SELECT_STATUS)
        .params(Map.of("account", accountId, "day", day))
        .query(JdbcLimitsRepository::mapStatus)
        .list()
        .stream()
        .sorted(Comparator.comparing(status -> status.limits().channel()))
        .toList();
  }

  @Override
  @Transactional
  public ChannelLimitsStatus save(String accountId, ChannelLimits limits, LocalDate day) {
    jdbc.sql(UPSERT_LIMITS)
        .params(
            Map.of(
                "account", accountId,
                "channel", limits.channel().name(),
                "daily", limits.dailyLimit().value(),
                "perOperation", limits.perOperationLimit().value()))
        .update();
    return findOne(accountId, limits.channel(), day).orElseThrow();
  }

  /**
   * READ COMMITTED fijo (no depende del default del pool o del servidor): CONSUME es seguro justo
   * porque, bajo este nivel, la sentencia que espera el bloqueo de fila reevalua su WHERE contra la
   * ultima version confirmada. Con REPEATABLE READ o SERIALIZABLE, PostgreSQL abortaria la segunda
   * transaccion con 40001 en lugar de reevaluar, y las validaciones concurrentes fallarian.
   */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Optional<ValidationResult> consume(
      String accountId, Channel channel, LocalDate day, Money amount) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      Optional<ValidationResult> applied = tryConsume(accountId, channel, day, amount);
      if (applied.isPresent()) {
        return applied;
      }
      // No se aplico: sin limites, o excede. Se lee el estado solo para explicar el rechazo.
      Optional<ChannelLimitsStatus> status = findOne(accountId, channel, day);
      if (status.isEmpty()) {
        return Optional.empty();
      }
      ValidationResult result = status.get().evaluate(amount);
      if (result instanceof Rejected) {
        return Optional.of(result);
      }
      // Aprobaria: el estado cambio entre la sentencia y la lectura (p. ej. subio el limite);
      // reintenta.
    }
    throw new IllegalStateException("No fue posible aplicar el consumo por cambios concurrentes.");
  }

  private Optional<ValidationResult> tryConsume(
      String accountId, Channel channel, LocalDate day, Money amount) {
    return jdbc.sql(CONSUME)
        .params(
            Map.of(
                "account",
                accountId,
                "channel",
                channel.name(),
                "day",
                day,
                "amount",
                amount.value()))
        .query(
            (rs, row) -> {
              Money consumed = Money.of(rs.getBigDecimal("consumed"));
              Money daily = Money.of(rs.getBigDecimal("daily_limit"));
              return (ValidationResult) new Approved(daily.minusFloorZero(consumed));
            })
        .optional();
  }

  private Optional<ChannelLimitsStatus> findOne(String accountId, Channel channel, LocalDate day) {
    return jdbc.sql(SELECT_STATUS + " AND l.channel = :channel")
        .params(Map.of("account", accountId, "channel", channel.name(), "day", day))
        .query(JdbcLimitsRepository::mapStatus)
        .optional();
  }

  private static ChannelLimitsStatus mapStatus(ResultSet rs, int row) throws SQLException {
    ChannelLimits limits =
        ChannelLimits.of(
            Channel.valueOf(rs.getString("channel")),
            rs.getBigDecimal("daily_limit"),
            rs.getBigDecimal("per_operation_limit"));
    return new ChannelLimitsStatus(limits, Money.of(rs.getBigDecimal("consumed")));
  }
}
