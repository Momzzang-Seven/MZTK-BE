package momzzangseven.mztkbe.modules.level.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RewardTxStatus unit test")
class RewardTxStatusTest {

  @Test
  @DisplayName("isPendingLike returns true for pending-like statuses")
  void isPendingLike_returnsTrueForPendingLikeStatuses() {
    assertThat(RewardTxStatus.CREATED.isPendingLike()).isTrue();
    assertThat(RewardTxStatus.SIGNED.isPendingLike()).isTrue();
    assertThat(RewardTxStatus.PENDING.isPendingLike()).isTrue();
    assertThat(RewardTxStatus.UNCONFIRMED.isPendingLike()).isTrue();
  }

  @Test
  @DisplayName("isPendingLike returns false for final statuses")
  void isPendingLike_returnsFalseForFinalStatuses() {
    assertThat(RewardTxStatus.SUCCEEDED.isPendingLike()).isFalse();
    assertThat(RewardTxStatus.FAILED_ONCHAIN.isPendingLike()).isFalse();
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> RewardTxStatus.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
