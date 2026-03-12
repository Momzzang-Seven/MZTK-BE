package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogSerializerTest {

  @Mock private ObjectMapper objectMapper;

  @Test
  void normalize_returnsEmptyForNullOrEmptyInput() {
    AuditLogSerializer serializer = new AuditLogSerializer(new ObjectMapper());

    assertThat(serializer.normalize(null)).isEmpty();
    assertThat(serializer.normalize(Map.of())).isEmpty();
  }

  @Test
  void normalize_normalizesNestedValuesAndDropsNulls() {
    AuditLogSerializer serializer = new AuditLogSerializer(new ObjectMapper());
    Map<String, Object> nestedInput = new LinkedHashMap<>();
    nestedInput.put("x", null);
    nestedInput.put("y", new BigInteger("3"));
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("a", null);
    input.put("b", new BigInteger("12"));
    input.put("nested", nestedInput);
    List<Object> listWithNull = new ArrayList<>();
    listWithNull.add(null);
    listWithNull.add(new BigInteger("5"));
    input.put("list", listWithNull);

    Map<String, Object> normalized = serializer.normalize(input);

    assertThat(normalized).containsKey("b");
    assertThat(normalized).containsKey("nested");
    assertThat(normalized).containsKey("list");
    assertThat(normalized).doesNotContainKey("a");
    @SuppressWarnings("unchecked")
    Map<String, Object> nested = (Map<String, Object>) normalized.get("nested");
    assertThat(nested).containsOnlyKeys("y");
    assertThat(normalized.get("list")).isEqualTo(List.of("5"));
  }

  @Test
  void normalize_dropsMapOrListThatBecomeEmpty() {
    AuditLogSerializer serializer = new AuditLogSerializer(new ObjectMapper());
    Map<String, Object> mapWithNullOnly = new LinkedHashMap<>();
    mapWithNullOnly.put("x", null);
    List<Object> listWithNullOnly = new ArrayList<>();
    listWithNullOnly.add(null);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("emptyMap", mapWithNullOnly);
    input.put("emptyList", listWithNullOnly);

    Map<String, Object> normalized = serializer.normalize(input);

    assertThat(normalized).isEmpty();
  }

  @Test
  void toJson_returnsNullWhenNothingToSerialize() {
    AuditLogSerializer serializer = new AuditLogSerializer(new ObjectMapper());
    Map<String, Object> onlyNull = new LinkedHashMap<>();
    onlyNull.put("k", null);

    assertThat(serializer.toJson(onlyNull)).isNull();
  }

  @Test
  void toJson_serializesNormalizedDetail() {
    AuditLogSerializer serializer = new AuditLogSerializer(new ObjectMapper());
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("amount", new BigInteger("10"));
    input.put("nullable", null);

    String json = serializer.toJson(input);

    assertThat(json).contains("\"amount\":\"10\"");
    assertThat(json).doesNotContain("nullable");
  }

  @Test
  void toJson_wrapsSerializationException() throws Exception {
    ObjectMapper copied = org.mockito.Mockito.mock(ObjectMapper.class);
    AuditLogSerializer serializer = new AuditLogSerializer(objectMapper);
    when(objectMapper.copy()).thenReturn(copied);
    when(copied.setSerializationInclusion(JsonInclude.Include.NON_NULL)).thenReturn(copied);
    when(copied.writeValueAsString(any(Map.class)))
        .thenThrow(new JsonProcessingException("boom") {});

    assertThatThrownBy(() -> serializer.toJson(Map.of("k", "v")))
        .isInstanceOf(Web3TransactionStateInvalidException.class)
        .hasMessage("Failed to serialize audit detail");
  }
}
