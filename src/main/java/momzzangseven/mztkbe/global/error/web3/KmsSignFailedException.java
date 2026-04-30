package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when an AWS KMS {@code Sign} request cannot be completed.
 *
 * <p>Causes include KMS API throttling, transient 5xx errors, IAM permission failures, key
 * unavailability (disabled / pending deletion / pending import), or unexpected SDK exceptions. The
 * caller treats this as a non-recoverable signing failure for the current attempt; whether to retry
 * is governed by upstream retry policies (e.g. the reward transaction worker), not by this
 * exception itself.
 *
 * <p>Use {@link SignatureRecoveryException} instead when the KMS call succeeded but the returned
 * DER signature could not be linked back to the expected Ethereum address.
 */
public class KmsSignFailedException extends BusinessException {

  /**
   * Create a new {@link KmsSignFailedException} with a descriptive message.
   *
   * @param message human-readable description of the KMS sign failure
   */
  public KmsSignFailedException(String message) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message);
  }

  /**
   * Create a new {@link KmsSignFailedException} wrapping an underlying cause.
   *
   * @param message human-readable description of the KMS sign failure
   * @param cause underlying exception that triggered the failure (typically an AWS SDK exception)
   */
  public KmsSignFailedException(String message, Throwable cause) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message, cause);
  }
}
