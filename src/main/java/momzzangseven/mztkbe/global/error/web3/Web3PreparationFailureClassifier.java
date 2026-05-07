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
    if (failure instanceof RetryableWeb3PreparationException) {
      return true;
    }
    return !(failure instanceof BusinessException);
  }
}
