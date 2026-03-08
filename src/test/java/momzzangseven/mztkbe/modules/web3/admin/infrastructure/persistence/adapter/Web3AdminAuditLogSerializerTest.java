package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Web3AdminAuditLogSerializerTest {

  @Test
  void toJson_returnsNull_whenDetailEmpty() {
    Web3AdminAuditLogSerializer serializer = new Web3AdminAuditLogSerializer(new ObjectMapper());

    assertThat(serializer.toJson(null)).isNull();
    assertThat(serializer.toJson(Map.of())).isNull();
  }

  @Test
  void toJson_serializesWithNonNullInclusion() {
    Web3AdminAuditLogSerializer serializer = new Web3AdminAuditLogSerializer(new ObjectMapper());
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("a", 1);
    detail.put("b", null);

    String json = serializer.toJson(detail);

    assertThat(json).contains("\"a\":1");
    assertThat(json).doesNotContain("\"b\"");
  }

  @Test
  void toJson_throwsIllegalState_whenSerializationFails() throws Exception {
    ObjectMapper mapper = mock(ObjectMapper.class);
    ObjectMapper copied = mock(ObjectMapper.class);
    when(mapper.copy()).thenReturn(copied);
    when(copied.setSerializationInclusion(JsonInclude.Include.NON_NULL)).thenReturn(copied);
    when(copied.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

    Web3AdminAuditLogSerializer serializer = new Web3AdminAuditLogSerializer(mapper);

    assertThatThrownBy(() -> serializer.toJson(Map.of("k", "v")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to serialize admin audit detail");
  }
}
