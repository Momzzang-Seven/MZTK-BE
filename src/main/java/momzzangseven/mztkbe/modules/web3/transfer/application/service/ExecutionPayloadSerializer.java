package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
/** Serializes execution payload snapshots and produces deterministic SHA-256 hash values. */
public class ExecutionPayloadSerializer {

  private final ObjectMapper objectMapper;

  /** Serializes arbitrary payload object into JSON text for snapshot persistence. */
  public String serialize(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize execution payload", e);
    }
  }

  /** Computes lowercase hex-encoded SHA-256 digest for serialized payload JSON. */
  public String hashHex(Object payload) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(serialize(payload).getBytes(StandardCharsets.UTF_8));
      return Numeric.toHexString(digest);
    } catch (Exception e) {
      throw new IllegalStateException("failed to hash execution payload", e);
    }
  }
}
