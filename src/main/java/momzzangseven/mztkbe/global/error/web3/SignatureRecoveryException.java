package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when an ECDSA signature cannot be linked back to the expected Ethereum address.
 *
 * <p>This typically indicates that neither {@code v=27} nor {@code v=28} produced a public key
 * whose derived address matches the wallet bound to the KMS key being used to sign. Such a failure
 * implies either a corrupted DER signature, a digest mismatch, or a misconfigured KMS key /
 * expected address pairing — all of which are non-recoverable from the caller's perspective.
 */
public class SignatureRecoveryException extends BusinessException {

  /**
   * Create a new {@link SignatureRecoveryException} with a descriptive message.
   *
   * @param message human-readable description of the recovery failure
   */
  public SignatureRecoveryException(String message) {
    super(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED, message);
  }

  /**
   * Create a new {@link SignatureRecoveryException} wrapping an underlying cause.
   *
   * @param message human-readable description of the recovery failure
   * @param cause underlying exception that triggered the failure
   */
  public SignatureRecoveryException(String message, Throwable cause) {
    super(ErrorCode.WEB3_SIGNATURE_RECOVERY_FAILED, message, cause);
  }
}
