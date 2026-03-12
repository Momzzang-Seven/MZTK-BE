package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3TxFailureReason unit test")
class Web3TxFailureReasonTest {

  @Test
  @DisplayName("code returns enum name")
  void code_returnsName() {
    assertThat(Web3TxFailureReason.RPC_UNAVAILABLE.code()).isEqualTo("RPC_UNAVAILABLE");
    assertThat(Web3TxFailureReason.RECEIPT_TIMEOUT.code()).isEqualTo("RECEIPT_TIMEOUT");
  }

  @Test
  @DisplayName("isRetryable reflects configured retry policy")
  void isRetryable_returnsConfiguredValue() {
    assertThat(Web3TxFailureReason.RPC_UNAVAILABLE.isRetryable()).isTrue();
    assertThat(Web3TxFailureReason.BROADCAST_FAILED.isRetryable()).isTrue();
    assertThat(Web3TxFailureReason.INVALID_SIGNED_TX.isRetryable()).isFalse();
    assertThat(Web3TxFailureReason.RECEIPT_TIMEOUT.isRetryable()).isFalse();
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3TxFailureReason.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
