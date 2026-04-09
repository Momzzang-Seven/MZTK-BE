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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminAuditDetailNormalizer 단위 테스트")
class AdminAuditDetailNormalizerTest {

  @Test
  @DisplayName("입력이 null 이면, normalize 는 빈 Map 을 반환한다")
  void normalize_returnsEmptyMap_whenInputIsNull() {
    assertThat(AdminAuditDetailNormalizer.normalize(null)).isEmpty();
  }

  @Test
  @DisplayName("입력이 빈 Map 이면, normalize 는 빈 Map 을 반환한다")
  void normalize_returnsEmptyMap_whenInputIsEmpty() {
    assertThat(AdminAuditDetailNormalizer.normalize(Map.of())).isEmpty();
  }

  @Test
  @DisplayName("값이 null 인 키가 포함되면, normalize 는 해당 키를 결과에서 제거한다")
  void normalize_dropsNullValues() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("kept", "value");
    input.put("dropped", null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(input);

    assertThat(result).containsOnlyKeys("kept");
  }

  @Test
  @DisplayName("BigInteger 값이 포함되면, normalize 는 정밀도 손실 없는 String 으로 변환한다")
  void normalize_convertsBigIntegerToString() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(
            Map.of("amount", new BigInteger("12345678901234567890")));

    assertThat(result).containsEntry("amount", "12345678901234567890");
  }

  @Test
  @DisplayName("BigDecimal 값이 포함되면, normalize 는 trailing zero 를 제거한 plain String 으로 변환한다")
  void normalize_convertsBigDecimalToPlainStringWithoutTrailingZeros() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(Map.of("price", new BigDecimal("12.3400")));

    assertThat(result).containsEntry("price", "12.34");
  }

  @Test
  @DisplayName("LocalDateTime 값이 포함되면, normalize 는 ISO-8601 형식 문자열로 변환한다")
  void normalize_convertsLocalDateTimeToIsoString() {
    LocalDateTime dt = LocalDateTime.of(2026, 4, 9, 10, 30, 0);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", dt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00");
  }

  @Test
  @DisplayName("Instant 값이 포함되면, normalize 는 ISO-8601 UTC 문자열로 변환한다")
  void normalize_convertsInstantToIsoString() {
    Instant instant = Instant.parse("2026-04-09T10:30:00Z");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", instant));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00Z");
  }

  @Test
  @DisplayName("OffsetDateTime 값이 포함되면, normalize 는 offset 을 보존한 ISO-8601 문자열로 변환한다")
  void normalize_convertsOffsetDateTimeToIsoString() {
    OffsetDateTime odt = OffsetDateTime.of(2026, 4, 9, 10, 30, 0, 0, ZoneOffset.ofHours(9));

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", odt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00+09:00");
  }

  @Test
  @DisplayName("ZonedDateTime 값이 포함되면, normalize 는 offset 을 보존한 ISO-8601 문자열로 변환한다")
  void normalize_convertsZonedDateTimeToIsoString() {
    ZonedDateTime zdt = ZonedDateTime.of(2026, 4, 9, 10, 30, 0, 0, ZoneOffset.ofHours(9));

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("at", zdt));

    assertThat(result).containsEntry("at", "2026-04-09T10:30:00+09:00");
  }

  @Test
  @DisplayName("Enum 값이 포함되면, normalize 는 enum.name() 문자열로 변환한다")
  void normalize_convertsEnumToName() {
    Map<String, Object> result =
        AdminAuditDetailNormalizer.normalize(Map.of("status", Status.ACTIVE));

    assertThat(result).containsEntry("status", "ACTIVE");
  }

  @Test
  @DisplayName("중첩된 Map 이 포함되면, normalize 는 내부 값까지 재귀적으로 정규화한다")
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
  @DisplayName("중첩 Map 의 모든 값이 null 이면, normalize 는 해당 Map 자체를 결과에서 제거한다")
  void normalize_dropsNestedMap_whenAllValuesAreNull() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("a", null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("nested", nested));

    assertThat(result).doesNotContainKey("nested");
  }

  @Test
  @DisplayName("List 값이 포함되면, normalize 는 각 요소를 재귀적으로 정규화한다")
  void normalize_recursivelyNormalizesList() {
    List<Object> list = List.of(new BigInteger("1"), Status.ACTIVE, "raw");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("items", list));

    assertThat(result).containsEntry("items", List.of("1", "ACTIVE", "raw"));
  }

  @Test
  @DisplayName("List 의 모든 요소가 null 이라 필터링 후 비게 되면, normalize 는 해당 List 자체를 제거한다")
  void normalize_dropsList_whenEmptyAfterFiltering() {
    java.util.ArrayList<Object> list = new java.util.ArrayList<>();
    list.add(null);
    list.add(null);

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("items", list));

    assertThat(result).doesNotContainKey("items");
  }

  @Test
  @DisplayName("Set 등 List/Map 이 아닌 Iterable 이 포함되면, normalize 는 List 로 변환하면서 각 요소를 정규화한다")
  void normalize_recursivelyNormalizesGenericIterable() {
    // Set is Iterable but neither List nor Map — exercises the dedicated Iterable<?> branch.
    Set<Object> values = new java.util.LinkedHashSet<>();
    values.add(BigInteger.TEN);
    values.add("raw");

    Map<String, Object> result = AdminAuditDetailNormalizer.normalize(Map.of("set", values));

    assertThat(result).containsKey("set");
    assertThat(result.get("set")).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> normalizedList = (List<Object>) result.get("set");
    assertThat(normalizedList).containsExactlyInAnyOrder("10", "raw");
  }

  @Test
  @DisplayName("primitive/Boolean/String 값이 포함되면, normalize 는 변환 없이 원본 그대로 통과시킨다")
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
