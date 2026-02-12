package momzzangseven.mztkbe.modules.web3.transaction.application.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utility to normalize audit detail values into JSON-friendly stable forms. */
public final class AuditDetailBuilder {

  private final Map<String, Object> detail = new LinkedHashMap<>();

  private AuditDetailBuilder() {}

  public static AuditDetailBuilder create() {
    return new AuditDetailBuilder();
  }

  public AuditDetailBuilder put(String key, Object value) {
    if (key == null || key.isBlank()) {
      return this;
    }
    detail.put(key, normalize(value));
    return this;
  }

  public AuditDetailBuilder putAll(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return this;
    }
    source.forEach(this::put);
    return this;
  }

  public Map<String, Object> build() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(detail));
  }

  private Object normalize(Object value) {
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
      Map<String, Object> normalized = new LinkedHashMap<>();
      mapValue.forEach(
          (key, mapEntryValue) -> normalized.put(String.valueOf(key), normalize(mapEntryValue)));
      return normalized;
    }
    if (value instanceof List<?> listValue) {
      List<Object> normalized = new ArrayList<>(listValue.size());
      listValue.forEach(item -> normalized.add(normalize(item)));
      return normalized;
    }
    if (value instanceof Iterable<?> iterableValue) {
      List<Object> normalized = new ArrayList<>();
      iterableValue.forEach(item -> normalized.add(normalize(item)));
      return normalized;
    }
    if (value.getClass().isArray() && value instanceof Object[] arrayValue) {
      List<Object> normalized = new ArrayList<>(arrayValue.length);
      for (Object item : arrayValue) {
        normalized.add(normalize(item));
      }
      return normalized;
    }
    return value;
  }
}
