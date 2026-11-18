package com.lab.transferlimits.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.transferlimits.domain.Channel;
import com.lab.transferlimits.domain.ChannelLimits;
import com.lab.transferlimits.domain.ChannelLimitsStatus;
import com.lab.transferlimits.domain.Money;
import com.lab.transferlimits.domain.RejectionReason;
import com.lab.transferlimits.domain.ValidationResult;
import com.lab.transferlimits.domain.ValidationResult.Approved;
import com.lab.transferlimits.domain.ValidationResult.Rejected;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

/**
 * Pruebas unitarias con JdbcClient simulado. La atomicidad real en PostgreSQL: docs/known-gaps.md.
 */
class JdbcLimitsRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
  private static final String CONSUME_FRAGMENT = "INSERT INTO daily_consumption";
  private static final String STATUS_FRAGMENT = "COALESCE(c.consumed";

  private final JdbcClient jdbc = mock(JdbcClient.class);
  private final JdbcLimitsRepository repository = new JdbcLimitsRepository(jdbc);

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static Money money(String value) {
    return Money.of(bd(value));
  }

  private static Map<String, Object> statusRow(
      String channel, String daily, String perOp, String used) {
    return Map.of(
        "channel", channel,
        "daily_limit", bd(daily),
        "per_operation_limit", bd(perOp),
        "consumed", bd(used));
  }

  private static ResultSet resultSet(Map<String, Object> row) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    for (Map.Entry<String, Object> entry : row.entrySet()) {
      if (entry.getValue() instanceof String text) {
        when(rs.getString(entry.getKey())).thenReturn(text);
      } else {
        when(rs.getBigDecimal(entry.getKey())).thenReturn((BigDecimal) entry.getValue());
      }
    }
    return rs;
  }

  /** Simula la sentencia que contiene fragment; cada llamada devuelve el siguiente resultado. */
  @SafeVarargs
  @SuppressWarnings({"unchecked", "rawtypes"})
  private StatementSpec stub(String fragment, List<Map<String, Object>>... successive) {
    StatementSpec spec = mock(StatementSpec.class);
    AtomicInteger calls = new AtomicInteger();
    when(jdbc.sql(contains(fragment))).thenReturn(spec);
    when(spec.params(anyMap())).thenReturn(spec);
    when(spec.query(any(RowMapper.class)))
        .thenAnswer(
            invocation -> {
              RowMapper<Object> mapper = invocation.getArgument(0);
              List<Map<String, Object>> rows =
                  successive[Math.min(calls.getAndIncrement(), successive.length - 1)];
              List<Object> mapped = new ArrayList<>();
              for (Map<String, Object> row : rows) {
                mapped.add(mapper.mapRow(resultSet(row), 0));
              }
              MappedQuerySpec<Object> query = mock(MappedQuerySpec.class);
              when(query.list()).thenReturn(mapped);
              when(query.optional()).thenReturn(mapped.stream().findFirst());
              return query;
            });
    return spec;
  }

  private static List<Map<String, Object>> none() {
    return List.of();
  }

  @Test
  @DisplayName("AC-15: findAll mapea limites y consumo y ordena por canal")
  void ac15FindAllMapsAndSorts() {
    stub(
        STATUS_FRAGMENT,
        List.of(statusRow("WEB", "2000", "500", "0"), statusRow("MOBILE", "5000", "1000", "1500")));

    List<ChannelLimitsStatus> result = repository.findAll("ACC-1", DAY);

    assertThat(result)
        .extracting(s -> s.limits().channel())
        .containsExactly(Channel.MOBILE, Channel.WEB);
    assertThat(result.getFirst().consumedToday()).isEqualTo(money("1500"));
    assertThat(result.getFirst().availableToday()).isEqualTo(money("3500"));
  }

  @Test
  @DisplayName("AC-10: findAll sin filas devuelve lista vacia")
  void ac10FindAllEmpty() {
    stub(STATUS_FRAGMENT, none());

    assertThat(repository.findAll("SIN", DAY)).isEmpty();
  }

  @Test
  @DisplayName("AC-12: save hace upsert de los limites y devuelve el estado del dia")
  void ac12SaveUpsertsAndReturnsStatus() {
    StatementSpec upsert = stub("INSERT INTO channel_limits", none());
    stub(STATUS_FRAGMENT, List.of(statusRow("WEB", "2000", "500", "0")));
    ChannelLimits limits = ChannelLimits.of(Channel.WEB, bd("2000"), bd("500"));

    ChannelLimitsStatus status = repository.save("ACC-1", limits, DAY);

    verify(upsert).update();
    assertThat(status.limits()).isEqualTo(limits);
    assertThat(status.consumedToday()).isEqualTo(Money.ZERO);
  }

  @Test
  @DisplayName("AC-16: consume aplicado es UNA sola sentencia, sin leer antes de escribir")
  void ac16ConsumeAppliedIsSingleStatement() {
    stub(CONSUME_FRAGMENT, List.of(Map.of("consumed", bd("300"), "daily_limit", bd("1000"))));

    Optional<ValidationResult> result =
        repository.consume("ACC-1", Channel.MOBILE, DAY, money("300"));

    assertThat(result).contains(new Approved(money("700")));
    verify(jdbc, times(1)).sql(anyString());
  }

  @Test
  @DisplayName("AC-16: la sentencia de consumo es INSERT ... ON CONFLICT DO UPDATE condicionada")
  void ac16ConsumeSqlIsConditionalUpsert() {
    assertThat(JdbcLimitsRepository.CONSUME)
        .contains("ON CONFLICT (account_id, channel, business_date) DO UPDATE")
        .contains("<=")
        .contains("RETURNING");
  }

  @Test
  @DisplayName("AC-10: consume sin limites configurados devuelve vacio")
  void ac10ConsumeWithoutLimits() {
    stub(CONSUME_FRAGMENT, none());
    stub(STATUS_FRAGMENT, none());

    assertThat(repository.consume("SIN", Channel.WEB, DAY, money("10"))).isEmpty();
  }

  @Test
  @DisplayName("AC-04 y AC-06: consume no aplicado por limite por operacion devuelve rechazo")
  void ac04ConsumeRejectedPerOperation() {
    stub(CONSUME_FRAGMENT, none());
    stub(STATUS_FRAGMENT, List.of(statusRow("WEB", "1000", "300", "0")));

    Optional<ValidationResult> result = repository.consume("ACC-1", Channel.WEB, DAY, money("400"));

    assertThat(result)
        .contains(
            new Rejected(
                RejectionReason.PER_OPERATION_LIMIT_EXCEEDED, money("300"), money("1000")));
  }

  @Test
  @DisplayName("AC-05 y AC-06: consume no aplicado por disponible del dia devuelve rechazo")
  void ac05ConsumeRejectedDaily() {
    stub(CONSUME_FRAGMENT, none());
    stub(STATUS_FRAGMENT, List.of(statusRow("WEB", "1000", "300", "900")));

    Optional<ValidationResult> result = repository.consume("ACC-1", Channel.WEB, DAY, money("150"));

    assertThat(result)
        .contains(new Rejected(RejectionReason.DAILY_LIMIT_EXCEEDED, money("300"), money("100")));
  }

  @Test
  @DisplayName("AC-16: si el estado cambia entre la sentencia y la lectura, reintenta")
  void ac16RetriesWhenStateChanged() {
    stub(
        CONSUME_FRAGMENT,
        none(),
        List.of(Map.of("consumed", bd("100"), "daily_limit", bd("1000"))));
    stub(STATUS_FRAGMENT, List.of(statusRow("WEB", "1000", "300", "0")));

    assertThat(repository.consume("ACC-1", Channel.WEB, DAY, money("100")))
        .contains(new Approved(money("900")));
  }

  @Test
  @DisplayName(
      "AC-17: si nunca se logra aplicar falla con error generico (no aprueba sin consumir)")
  void ac17GivesUpAfterMaxAttempts() {
    stub(CONSUME_FRAGMENT, none());
    stub(STATUS_FRAGMENT, List.of(statusRow("WEB", "1000", "300", "0")));

    assertThatThrownBy(() -> repository.consume("ACC-1", Channel.WEB, DAY, money("100")))
        .isInstanceOf(IllegalStateException.class);
  }
}
