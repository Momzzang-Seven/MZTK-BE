package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * EIP-712 TypedData Structure
 *
 * <p>Complete TypedData structure according to EIP-712 standard.
 *
 * <p>Reproduces the JSON structure from frontend exactly for signature verification.
 */
@Getter
@Builder
public class TypedData {
  /** Domain separator */
  private final EIP712Domain domain;

  /** Message to sign */
  private final EIP712Message message;

  /** Primary type name (e.g., "AuthRequest") */
  private final String primaryType;

  /**
   * Create TypedData for wallet registration
   *
   * @param domain domain configuration
   * @param message challenge message
   * @return TypedData instance
   */
  public static TypedData forWalletRegistration(EIP712Domain domain, EIP712Message message) {
    if (domain == null) {
      throw new IllegalArgumentException("Domain must not be null");
    }
    if (message == null) {
      throw new IllegalArgumentException("Message must not be null");
    }

    domain.validate();
    message.validate();

    return TypedData.builder().domain(domain).message(message).primaryType("AuthRequest").build();
  }
}
