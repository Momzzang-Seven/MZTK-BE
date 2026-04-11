package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
public class QnaPayloadSerializer {

  private final ObjectMapper objectMapper;

  public String serialize(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize qna execution payload", e);
    }
  }

  public String hashHex(Object payload) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(serialize(payload).getBytes(StandardCharsets.UTF_8));
      return Numeric.toHexString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("failed to hash qna execution payload", e);
    }
  }
}
