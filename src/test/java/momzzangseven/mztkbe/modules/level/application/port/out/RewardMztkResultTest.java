package momzzangseven.mztkbe.modules.level.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.Test;

class RewardMztkResultTest {

  @Test
  void constructor_rejectsNullStatus() {
    assertThatThrownBy(() -> new RewardMztkResult(null, null, null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void factoryMethods_buildExpectedStatus() {
    assertThat(RewardMztkResult.created("QUEUED").status()).isEqualTo(RewardTxStatus.CREATED);
    assertThat(RewardMztkResult.pending("0x" + "a".repeat(64)).status())
        .isEqualTo(RewardTxStatus.PENDING);
    assertThat(RewardMztkResult.success("0x" + "a".repeat(64)).status())
        .isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(RewardMztkResult.unconfirmed("TIMEOUT", "0x" + "a".repeat(64)).status())
        .isEqualTo(RewardTxStatus.UNCONFIRMED);
  }
}
