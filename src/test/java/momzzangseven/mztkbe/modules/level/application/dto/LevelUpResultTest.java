package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.Test;

class LevelUpResultTest {

  @Test
  void builder_shouldBuildResult() {
    LevelUpResult result =
        LevelUpResult.builder()
            .levelUpHistoryId(5L)
            .fromLevel(2)
            .toLevel(3)
            .spentXp(200)
            .rewardMztk(10)
            .rewardStatus(RewardStatus.SUCCESS)
            .rewardTxStatus(RewardTxStatus.SUCCEEDED)
            .rewardTxPhase(RewardTxPhase.SUCCESS)
            .rewardTxHash("0x123")
            .rewardExplorerUrl("https://explorer/tx/0x123")
            .build();

    assertThat(result.toLevel()).isEqualTo(3);
    assertThat(result.rewardStatus()).isEqualTo(RewardStatus.SUCCESS);
    assertThat(result.rewardExplorerUrl()).contains("0x123");
  }
}
