package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when an AWS KMS {@code Sign} request cannot be completed.
 *
 * <p>Causes include KMS API throttling, transient 5xx errors, IAM permission failures, key
 * unavailability (disabled / pending deletion / pending import), or unexpected SDK exceptions.
 *
 * <p>The {@code retryable} flag distinguishes transient AWS conditions (throttling, 5xx, network
 * timeouts) from terminal configuration errors (AccessDenied, NotFound, Disabled, KMSInvalidState,
 * InvalidKeyUsage, UnsupportedOperation). The signing adapters ({@code KmsSignerAdapter}, {@code
 * LocalEcSignerAdapter}) own the classification at the throw site by consulting {@code
 * KmsClientErrorClassifier.isTerminalCause(...)}, so direct propagators (e.g. {@code
 * ProvisionTreasuryKeyService} sanity-sign) surface an accurate {@code retryable} signal in the
 * HTTP response without having to re-classify themselves. Callers that additionally map terminal
 * failures to a quarantine reason code (transaction issuer worker, eip-7702 sponsor delegates) keep
 * inspecting the cause via {@code KmsClientErrorClassifier.isTerminal()} and re-throw an {@link
 * ExecutionIntentTerminalException} with {@code retryable=false} for terminal cases.
 *
 * <p>Use {@link SignatureRecoveryException} instead when the KMS call succeeded but the returned
 * DER signature could not be linked back to the expected Ethereum address.
 */
public class KmsSignFailedException extends Web3TransferException {

  /**
   * Create a new {@link KmsSignFailedException} with a descriptive message. Defaults to {@code
   * retryable=true}.
   *
   * @param message human-readable description of the KMS sign failure
   */
  public KmsSignFailedException(String message) {
    this(message, true);
  }

  /**
   * Create a new {@link KmsSignFailedException} with a descriptive message and explicit retryable
   * signal.
   *
   * @param message human-readable description of the KMS sign failure
   * @param retryable {@code true} for transient AWS conditions (throttling, 5xx); {@code false} for
   *     terminal configuration errors (AccessDenied, NotFound, Disabled, ...)
   */
  public KmsSignFailedException(String message, boolean retryable) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message, retryable);
  }

  /**
   * Create a new {@link KmsSignFailedException} wrapping an underlying cause. Defaults to {@code
   * retryable=true}.
   *
   * @param message human-readable description of the KMS sign failure
   * @param cause underlying exception that triggered the failure (typically an AWS SDK exception)
   */
  public KmsSignFailedException(String message, Throwable cause) {
    this(message, cause, true);
  }

  /**
   * Create a new {@link KmsSignFailedException} wrapping an underlying cause with explicit
   * retryable signal.
   *
   * @param message human-readable description of the KMS sign failure
   * @param cause underlying exception that triggered the failure (typically an AWS SDK exception)
   * @param retryable {@code true} for transient AWS conditions (throttling, 5xx); {@code false} for
   *     terminal configuration errors (AccessDenied, NotFound, Disabled, ...)
   */
  public KmsSignFailedException(String message, Throwable cause, boolean retryable) {
    super(ErrorCode.WEB3_KMS_SIGN_FAILED, message, cause, retryable);
  }
}
