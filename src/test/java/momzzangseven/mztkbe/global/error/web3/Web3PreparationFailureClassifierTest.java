package momzzangseven.mztkbe.global.error.web3;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import org.junit.jupiter.api.Test;

class Web3PreparationFailureClassifierTest {

  @Test
  void isRetryable_returnsTrueForRetryablePreparationMarker() {
    RuntimeException failure = new RetryableWeb3PreparationException("message can change freely");

    assertThat(Web3PreparationFailureClassifier.isRetryable(failure)).isTrue();
  }

  @Test
  void isRetryable_returnsFalseForPlainWeb3InvalidInput() {
    RuntimeException failure =
        new Web3InvalidInputException("conflicting active text alone is not policy");

    assertThat(Web3PreparationFailureClassifier.isRetryable(failure)).isFalse();
  }

  @Test
  void isRetryable_followsWeb3TransferExceptionPolicy() {
    assertThat(
            Web3PreparationFailureClassifier.isRetryable(
                new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true)))
        .isTrue();
    assertThat(
            Web3PreparationFailureClassifier.isRetryable(
                new Web3TransferException(ErrorCode.DELEGATE_NOT_ALLOWLISTED, false)))
        .isFalse();
  }

  @Test
  void isRetryable_returnsFalseForOtherBusinessException() {
    assertThat(Web3PreparationFailureClassifier.isRetryable(new WalletNotConnectedException(7L)))
        .isFalse();
  }

  @Test
  void isRetryable_returnsTrueForUnexpectedRuntimeException() {
    assertThat(Web3PreparationFailureClassifier.isRetryable(new IllegalStateException("timeout")))
        .isTrue();
  }
}
