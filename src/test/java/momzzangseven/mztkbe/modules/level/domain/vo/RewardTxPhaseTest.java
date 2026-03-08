package momzzangseven.mztkbe.modules.level.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RewardTxPhase unit test")
class RewardTxPhaseTest {

  @Test
  @DisplayName("from maps success and failure statuses")
  void from_mapsSuccessAndFailure() {
    assertThat(RewardTxPhase.from(RewardTxStatus.SUCCEEDED)).isEqualTo(RewardTxPhase.SUCCESS);
    assertThat(RewardTxPhase.from(RewardTxStatus.FAILED_ONCHAIN)).isEqualTo(RewardTxPhase.FAILED);
  }

  @Test
  @DisplayName("from maps pending-like statuses to PENDING")
  void from_mapsPendingStatuses() {
    assertThat(RewardTxPhase.from(RewardTxStatus.CREATED)).isEqualTo(RewardTxPhase.PENDING);
    assertThat(RewardTxPhase.from(RewardTxStatus.SIGNED)).isEqualTo(RewardTxPhase.PENDING);
    assertThat(RewardTxPhase.from(RewardTxStatus.PENDING)).isEqualTo(RewardTxPhase.PENDING);
    assertThat(RewardTxPhase.from(RewardTxStatus.UNCONFIRMED)).isEqualTo(RewardTxPhase.PENDING);
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> RewardTxPhase.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
