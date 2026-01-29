package momzzangseven.mztkbe.modules.web3.signature.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * EIP-712 Domain Separator
 *
 * <p>Defines domain separator to provide signature context.
 *
 * <p>Replay attack prevention: Signatures cannot be reused on different dApps or chains.
 */
@Getter
@Builder
public class EIP712Domain {
  /** Application name (e.g., "MyWorkoutService") */
  private final String name;

  /** Version (e.g., "1") */
  private final String version;

  /** Chain ID (e.g., 11155111 for Sepolia) */
  private final Long chainId;

  /** Verifying contract address */
  private final String verifyingContract;

  /** Validate domain values */
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Domain name must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Domain version must not be blank");
    }
    if (chainId == null || chainId <= 0) {
      throw new IllegalArgumentException("Chain ID must be positive");
    }
    if (verifyingContract == null || !verifyingContract.matches("^0x[0-9a-fA-F]{40}$")) {
      throw new IllegalArgumentException("Invalid verifying contract address");
    }
  }
}
