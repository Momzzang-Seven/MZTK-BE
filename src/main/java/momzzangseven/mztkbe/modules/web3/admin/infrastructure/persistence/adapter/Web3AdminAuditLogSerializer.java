package momzzangseven.mztkbe.modules.web3.admin.infrastructure.persistence.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Web3AdminAuditLogSerializer {

  private final ObjectMapper objectMapper;

  public String toJson(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return objectMapper
          .copy()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize admin audit detail", e);
    }
  }
}
