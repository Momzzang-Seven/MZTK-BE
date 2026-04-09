package momzzangseven.mztkbe.global.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminAuditDetailNormalizerTest {

  @Test
  void normalize_returnsEmptyMap_whenInputIsNull() {
    assertThat(AdminAuditDetailNormalizer.normalize(null)).isEmpty();
  }

  @Test
  void normalize_returnsEmptyMap_whenInputIsEmpty() {
    assertThat(AdminAuditDetailNormalizer.normalize(Map.of())).isEmpty();
  }

  @Test
  void normalize_dropsNullValues() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("kept", "value");
    input.put("dropped", null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(input);

    assertThat(result).containsOnlyKeys("kept");
  }

  @Test
  void normalize_convertsBigIntegerToString() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(
            Map.of("amount", new BigInteger("12345678901234567890")));

    assertThat(result).containsEntry("amount", "12345678901234567890");
  }

  @Test
  void normalize_convertsBigDecimalToPlainStringWithoutTrailingZeros() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(Map.of("price", new BigDecimal("12.3400")));

    assertThat(result).containsEntry("price", "12.34");
  }

  @Test
  void normalize_convertsLocalDateTimeToIsoString() {
    LocalDateTime dt = LocalDateTime.of(2026, 4, 9, 10, 30, 0);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", dt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00");
  }

  @Test
  void normalize_convertsInstantToIsoString() {
    Instant instant = Instant.parse("2026-04-09T10:30:00Z");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", instant));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00Z");
  }

  @Test
  void normalize_convertsOffsetDateTimeToIsoString() {
    OffsetDateTime odt = OffsetDateTime.of(2026, 4, 9, 10, 30, 0, 0, ZoneOffset.ofHours(9));

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", odt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00+09:00");
  }

  @Test
  void normalize_convertsZonedDateTimeToIsoString() {
    ZonedDateTime zdt = ZonedDateTime.of(2026, 4, 9, 10, 30, 0, 0, ZoneOffset.ofHours(9));

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", zdt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00+09:00");
  }

  @Test
  void normalize_convertsEnumToName() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(Map.of("status", Status.ACTIVE));

    assertThat(result).containsEntry("status", "ACTIVE");
  }

  @Test
  void normalize_recursivelyNormalizesNestedMap() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("amount", new BigInteger("100"));
    nested.put("status", Status.ACTIVE);
    nested.put("dropped", null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("nested", nested));

    @SuppressWarnings("unchecked")
    Map<String, Object> normalizedNested = (Map<String, Object>) result.get("nested");
    assertThat(normalizedNested).containsEntry("amount", "100");
    assertThat(normalizedNested).containsEntry("status", "ACTIVE");
    assertThat(normalizedNested).doesNotContainKey("dropped");
  }

  @Test
  void normalize_dropsNestedMap_whenAllValuesAreNull() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("a", null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("nested", nested));

    assertThat(result).doesNotContainKey("nested");
  }

  @Test
  void normalize_recursivelyNormalizesList() {
    List<Object> list = List.of(new BigInteger("1"), Status.ACTIVE, "raw");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("items", list));

    assertThat(result).containsEntry("items", List.of("1", "ACTIVE", "raw"));
  }

  @Test
  void normalize_dropsList_whenEmptyAfterFiltering() {
    java.util.ArrayList<Object> list = new java.util.ArrayList<>();
    list.add(null);
    list.add(null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("items", list));

    assertThat(result).doesNotContainKey("items");
  }

  @Test
  void normalize_passesThroughPrimitivesAndStrings() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("count", 42);
    input.put("flag", true);
    input.put("name", "alice");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(input);

    assertThat(result).containsEntry("count", 42);
    assertThat(result).containsEntry("flag", true);
    assertThat(result).containsEntry("name", "alice");
  }

  private enum Status {
    ACTIVE
  }
}
