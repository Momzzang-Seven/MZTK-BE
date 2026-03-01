package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.Test;

class AuditDetailBuilderTest {

  @Test
  void put_ignoresNullOrBlankKey() {
    Map<String, Object> result =
        AuditDetailBuilder.create().put(null, "v1").put(" ", "v2").put("ok", "v3").build();

    assertThat(result).containsOnly(entry("ok", "v3"));
  }

  @Test
  void put_normalizesPrimitiveAndTimeLikeValues() {
    LocalDateTime local = LocalDateTime.of(2026, 3, 1, 10, 11, 12);
    Instant instant = Instant.parse("2026-03-01T01:02:03Z");
    OffsetDateTime offset = OffsetDateTime.parse("2026-03-01T01:02:03+09:00");
    ZonedDateTime zoned = ZonedDateTime.of(2026, 3, 1, 1, 2, 3, 0, ZoneOffset.UTC);

    Map<String, Object> result =
        AuditDetailBuilder.create()
            .put("bigInt", new BigInteger("123"))
            .put("bigDec", new BigDecimal("100.2300"))
            .put("local", local)
            .put("instant", instant)
            .put("offset", offset)
            .put("zoned", zoned)
            .put("enum", Web3TxStatus.SIGNED)
            .build();

    assertThat(result.get("bigInt")).isEqualTo("123");
    assertThat(result.get("bigDec")).isEqualTo("100.23");
    assertThat(result.get("local")).isEqualTo("2026-03-01T10:11:12");
    assertThat(result.get("instant")).isEqualTo("2026-03-01T01:02:03Z");
    assertThat(result.get("offset")).isEqualTo("2026-03-01T01:02:03+09:00");
    assertThat(result.get("zoned")).isEqualTo("2026-03-01T01:02:03Z");
    assertThat(result.get("enum")).isEqualTo("SIGNED");
  }

  @Test
  void put_normalizesNestedMapListIterableAndArray() {
    Map<String, Object> nested =
        Map.of(
            "amount", new BigInteger("99"),
            "items", List.of(new BigDecimal("1.20"), Web3TxStatus.SUCCEEDED),
            "iterable", (Iterable<Integer>) List.of(1, 2),
            "array", new Object[] {new BigInteger("7"), "ok"},
            "primitiveArray", new int[] {1, 2});

    Map<String, Object> result = AuditDetailBuilder.create().put("nested", nested).build();

    assertThat(result.get("nested")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> nestedResult = (Map<String, Object>) result.get("nested");
    assertThat(nestedResult.get("amount")).isEqualTo("99");
    assertThat(nestedResult.get("items")).isEqualTo(List.of("1.2", "SUCCEEDED"));
    assertThat(nestedResult.get("iterable")).isEqualTo(List.of(1, 2));
    assertThat(nestedResult.get("array")).isEqualTo(List.of("7", "ok"));
    assertThat(nestedResult.get("primitiveArray")).isInstanceOf(int[].class);
  }

  @Test
  void putAll_skipsNullOrEmptySource() {
    Map<String, Object> result =
        AuditDetailBuilder.create()
            .put("a", 1)
            .putAll(null)
            .putAll(Map.of())
            .putAll(Map.of("b", new BigInteger("2")))
            .build();

    assertThat(result).containsEntry("a", 1).containsEntry("b", "2");
  }

  @Test
  void build_returnsUnmodifiableCopy() {
    AuditDetailBuilder builder = AuditDetailBuilder.create().put("a", 1);
    Map<String, Object> built = builder.build();
    builder.put("b", 2);

    assertThat(built).containsOnly(entry("a", 1));
    assertThatThrownBy(() -> built.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
  }
}
