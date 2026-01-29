package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * EIP-712 Message (AuthRequest)
 *
 * <p>Model containing the actual message content to be signed.
 */
@Getter
@Builder
public class EIP712Message {
  /** Challenge message content */
  private final String content;

  /** Nonce (UUID) */
  private final String nonce;

  /** Validate message values */
  public void validate() {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Message content must not be blank");
    }
    if (nonce == null || nonce.isBlank()) {
      throw new IllegalArgumentException("Nonce must not be blank");
    }
  }
}
