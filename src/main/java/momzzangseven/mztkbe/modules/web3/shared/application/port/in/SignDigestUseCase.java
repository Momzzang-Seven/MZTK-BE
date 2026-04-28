package momzzangseven.mztkbe.modules.web3.shared.application.port.in;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;

/**
 * Cross-module entry point for digest-based ECDSA signing through AWS KMS.
 *
 * <p>Callers (the {@code transaction}, {@code eip7702}, and {@code treasury} modules) compute a
 * 32-byte EIP-1559 / EIP-7702 digest, then invoke this use case via their own bridging adapter to
 * obtain the canonical {@code (r, s, v)} components. The implementation handles low-s correction
 * and recovery-id determination so the result is directly consumable by typed-transaction encoders.
 */
public interface SignDigestUseCase {

  /**
   * Sign the supplied 32-byte digest with the KMS key identified by {@code command.kmsKeyId()} and
   * return the canonical Ethereum signature components.
   *
   * @param command validated command carrying the KMS key id, the 32-byte digest, and the wallet
   *     address that the recovered public key must match
   * @return canonical {@code (r, s, v)} signature, defensively copied
   */
  SignDigestResult execute(SignDigestCommand command);
}
