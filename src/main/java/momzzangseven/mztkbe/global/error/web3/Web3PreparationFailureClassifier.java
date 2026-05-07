package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;

/**
 * Classifies Web3 preparation failures so transient sync states do not become permanent failures.
 */
public final class Web3PreparationFailureClassifier {

  private Web3PreparationFailureClassifier() {}

  public static boolean isRetryable(RuntimeException failure) {
    if (failure instanceof Web3TransferException web3Failure) {
      return web3Failure.isRetryable();
    }
    if (failure instanceof Web3InvalidInputException web3Failure) {
      return isRetryableInvalidInput(web3Failure.getMessage());
    }
    return !(failure instanceof BusinessException);
  }

  private static boolean isRetryableInvalidInput(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    String normalized = message.toLowerCase();
    return normalized.contains("conflicting active")
        || normalized.contains("not registered onchain yet")
        || normalized.contains("differs from latest onchain projection")
        || normalized.contains("pending onchain mutation")
        || normalized.contains("wait for");
  }
}
