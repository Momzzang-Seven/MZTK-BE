package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when an AWS KMS {@code Sign} request cannot be completed.
 *
 * <p>Causes include KMS API throttling, transient 5xx errors, IAM permission failures, key
 * unavailability (disabled / pending deletion / pending import), or unexpected SDK exceptions.
 *
 * <p>The exception always carries {@code retryable=true} so that HTTP clients receive a consistent
 * retry signal for transient AWS conditions. Callers that need to distinguish terminal from
 * transient KMS failures (e.g. transaction issuer worker, eip-7702 sponsor delegates) inspect the
 * underlying cause via {@code KmsClientErrorClassifier.isTerminal()} and re-throw an
 * {@link ExecutionIntentTerminalException} with {@code retryable=false} for terminal cases.
 *
 * <p>Use {@link SignatureRecoveryException} instead when the KMS call succeeded but the returned
 * DER signature could not be linked back to the expected Ethereum address.
 */
public class KmsSignFailedException extends Web3TransferException {

  /**
   * Create a new {@link KmsSignFailedException} with a descriptive message.
   *
   * @param message human-readable description of the KMS sign failure
   */
  public KmsSignFailedException(String message) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message, true);
  }

  /**
   * Create a new {@link KmsSignFailedException} wrapping an underlying cause.
   *
   * @param message human-readable description of the KMS sign failure
   * @param cause underlying exception that triggered the failure (typically an AWS SDK exception)
   */
  public KmsSignFailedException(String message, Throwable cause) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message, cause, true);
  }
}
