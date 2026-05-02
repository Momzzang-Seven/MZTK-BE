package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.strategy;

import java.util.Set;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * Classifies a {@link KmsSignFailedException} cause to decide whether the worker should retry the
 * row or terminal-fail it.
 *
 * <p>Background: before this classifier, every KMS sign failure routed through {@code retry()},
 * which left rows with {@code KMS_SIGN_FAILED} eligible for re-claim every worker tick. AWS-side
 * "won't ever succeed" conditions (IAM deny, disabled key, invalid key usage, ...) therefore
 * looped forever, billing every retry to KMS. Surface those as terminal so they exit the queue
 * after one log line.
 *
 * <p>Non-terminal cases (network timeout, 5xx, throttling, unknown cause) keep the previous retry
 * behavior — the heavier {@code attempt_count}-based cap is tracked as a follow-up (F-2).
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
   * Returns {@code true} when the underlying cause indicates an unrecoverable KMS condition that
   * the worker should not keep retrying. Returns {@code false} for transient SDK client errors,
   * non-KMS causes, and missing error metadata — the caller falls back to retry.
   */
  public static boolean isTerminal(KmsSignFailedException ex) {
    Throwable cause = ex.getCause();
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
