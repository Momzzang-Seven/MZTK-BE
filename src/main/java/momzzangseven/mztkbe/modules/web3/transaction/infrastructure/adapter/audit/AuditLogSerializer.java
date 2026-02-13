package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.Web3TransactionStateInvalidException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditLogSerializer {

  private final ObjectMapper objectMapper;

  public String toJson(Map<String, Object> detail) {
    Map<String, Object> normalized = normalize(detail);
    if (normalized.isEmpty()) {
      return null;
    }
    try {
      return objectMapper
          .copy()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .writeValueAsString(normalized);
    } catch (JsonProcessingException e) {
      throw new Web3TransactionStateInvalidException("Failed to serialize audit detail", e);
    }
  }

  public Map<String, Object> normalize(Map<String, Object> detail) {
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

  private Object normalizeValue(Object value) {
    if (value == null) {
      return null;
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
      List<?> normalizedList =
          listValue.stream().map(this::normalizeValue).filter(v -> v != null).toList();
      return normalizedList.isEmpty() ? null : normalizedList;
    }
    return AuditDetailBuilder.create().put("value", value).build().get("value");
  }
}
