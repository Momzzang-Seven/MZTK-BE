package momzzangseven.mztkbe.modules.web3.shared.application.util;

import java.util.Set;
import momzzangseven.mztkbe.global.error.BusinessException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * Classifies a KMS-related {@link BusinessException} cause to decide whether the caller should
 * retry the row or terminal-fail it.
 *
 * <p>Background: before this classifier, every KMS sign failure routed through {@code retry()},
 * which left rows with {@code KMS_SIGN_FAILED} eligible for re-claim every worker tick. AWS-side
 * "won't ever succeed" conditions (IAM deny, disabled key, invalid key usage, ...) therefore looped
 * forever, billing every retry to KMS. Surface those as terminal so they exit the queue after one
 * log line.
 *
 * <p>Accepts any {@code BusinessException} whose cause is a {@code KmsException} so the same
 * decision applies to both {@code KmsSignFailedException} (write path) and {@code
 * KmsKeyDescribeFailedException} (read path) — both wrap AWS SDK errors with identical
 * terminal/transient semantics.
 *
 * <p>Lives in {@code web3/shared/application/util} so any sibling web3 module that calls a KMS path
 * can reuse the same terminal-vs-transient decision (transaction issuer batch, execution internal
 * issuer, eip-7702 sponsor preflight, ...).
 */
public final class KmsClientErrorClassifier {

  private static final Set<String> TERMINAL_AWS_ERROR_CODES =
      Set.of(
          "AccessDeniedException",
          "DisabledException",
          "KMSInvalidStateException",
          "InvalidKeyUsageException",
          "NotFoundException",
          "UnsupportedOperationException");

  private KmsClientErrorClassifier() {}

  /**
   * Returns {@code true} when the underlying cause of {@code ex} indicates an unrecoverable KMS
   * condition that the caller should not keep retrying. Returns {@code false} for transient SDK
   * client errors, non-KMS causes, and missing error metadata — the caller falls back to retry.
   */
  public static boolean isTerminal(BusinessException ex) {
    return isTerminalCause(ex.getCause());
  }

  /**
   * Cause-level entry point for classifying a raw {@link Throwable} (typically an {@link
   * software.amazon.awssdk.core.exception.SdkException} caught at the adapter boundary). Returns
   * {@code true} only when {@code cause} is a {@link KmsException} carrying one of the terminal AWS
   * error codes; everything else (null, non-KMS exceptions, missing {@link AwsErrorDetails}) is
   * treated as retryable.
   *
   * <p>Adapters use this overload to decide the {@code retryable} flag at the throw site so direct
   * propagators (e.g. {@code ProvisionTreasuryKeyService} sanity-sign) surface the correct retry
   * signal in the HTTP response without reclassifying themselves.
   */
  public static boolean isTerminalCause(Throwable cause) {
    if (!(cause instanceof KmsException kex)) {
      return false;
    }
    AwsErrorDetails details = kex.awsErrorDetails();
    if (details == null) {
      return false;
    }
    return TERMINAL_AWS_ERROR_CODES.contains(details.errorCode());
  }
}
