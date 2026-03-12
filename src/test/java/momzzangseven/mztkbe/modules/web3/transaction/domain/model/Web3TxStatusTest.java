package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Web3TxStatus unit test")
class Web3TxStatusTest {

  @Test
  @DisplayName("isPendingLike returns true for pending-like statuses")
  void isPendingLike_returnsTrueForPendingLikeStatuses() {
    assertThat(Web3TxStatus.CREATED.isPendingLike()).isTrue();
    assertThat(Web3TxStatus.SIGNED.isPendingLike()).isTrue();
    assertThat(Web3TxStatus.PENDING.isPendingLike()).isTrue();
    assertThat(Web3TxStatus.UNCONFIRMED.isPendingLike()).isTrue();
  }

  @Test
  @DisplayName("isPendingLike returns false for final statuses")
  void isPendingLike_returnsFalseForFinalStatuses() {
    assertThat(Web3TxStatus.SUCCEEDED.isPendingLike()).isFalse();
    assertThat(Web3TxStatus.FAILED_ONCHAIN.isPendingLike()).isFalse();
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> Web3TxStatus.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
