package momzzangseven.mztkbe.modules.web3.shared.application.port.in;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;

/**
 * Cross-module entry point for read-only KMS key state lookup.
 *
 * <p>Used by {@code VerifyTreasuryWalletForSignUseCase} (and any other gate that runs immediately
 * before a {@code Sign} attempt) to confirm the underlying KMS key is currently signable. The
 * implementation may apply a short-lived cache to absorb burst traffic from a single signing batch
 * — see the design doc §9 for the 60-second TTL rationale.
 */
public interface DescribeKmsKeyUseCase {

  /**
   * Look up the lifecycle state of the supplied KMS key id.
   *
   * @param kmsKeyId fully-qualified KMS key id (or alias) that the caller wants to describe
   * @return {@link KmsKeyState} representing the key's current signability
   */
  KmsKeyState execute(String kmsKeyId);
}
