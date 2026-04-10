package momzzangseven.mztkbe.global.audit.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes admin audit detail values into JSON-friendly stable forms.
 *
 * <p>Used by {@code AdminOnlyAspect} to prepare the {@code rawDetail} map before handing it to a
 * {@code RecordAdminAuditPort} adapter. Lives in {@code application} (not {@code infrastructure})
 * because the driving adapter (the aspect) is the only caller; serialization to JSON is handled
 * separately inside the persistence adapter.
 *
 * <p>This is a deliberate fork of {@code modules/web3/transaction/.../AuditDetailBuilder} — the two
 * normalizers may evolve independently as web3 adds module-specific value types.
 */
public final class AdminAuditDetailNormalizer {

  private AdminAuditDetailNormalizer() {}

  /**
   * Returns a normalized copy of {@code detail} with null values dropped and complex types coerced
   * into stable string/collection forms.
   */
  public static Map<String, Object> normalize(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    detail.forEach(
        (key, value) -> {
          Object normalizedValue = normalizeValue(value);
          if (normalizedValue != null) {
            normalized.put(key, normalizedValue);
          }
        });
    return normalized;
  }

  private static Object normalizeValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigInteger bigIntegerValue) {
      return bigIntegerValue.toString();
    }
    if (value instanceof BigDecimal bigDecimalValue) {
      return bigDecimalValue.stripTrailingZeros().toPlainString();
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    if (value instanceof Instant instant) {
      return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
    if (value instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    if (value instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    if (value instanceof Map<?, ?> mapValue) {
      Map<String, Object> normalizedMap = new LinkedHashMap<>();
      mapValue.forEach(
          (key, nestedValue) -> {
            Object normalizedNestedValue = normalizeValue(nestedValue);
            if (normalizedNestedValue != null) {
              normalizedMap.put(String.valueOf(key), normalizedNestedValue);
            }
          });
      return normalizedMap.isEmpty() ? null : normalizedMap;
    }
    if (value instanceof List<?> listValue) {
      List<Object> normalizedList = new ArrayList<>(listValue.size());
      for (Object item : listValue) {
        Object normalizedItem = normalizeValue(item);
        if (normalizedItem != null) {
          normalizedList.add(normalizedItem);
        }
      }
      return normalizedList.isEmpty() ? null : normalizedList;
    }
    if (value instanceof Iterable<?> iterableValue) {
      List<Object> normalizedList = new ArrayList<>();
      iterableValue.forEach(
          item -> {
            Object normalizedItem = normalizeValue(item);
            if (normalizedItem != null) {
              normalizedList.add(normalizedItem);
            }
          });
      return normalizedList.isEmpty() ? null : normalizedList;
    }
    return value;
  }
}
