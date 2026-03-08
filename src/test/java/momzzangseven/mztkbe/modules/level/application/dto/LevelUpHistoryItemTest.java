package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.Test;

class LevelUpHistoryItemTest {

  @Test
  void builder_shouldPopulateAllFields() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 2, 28, 12, 0);

    LevelUpHistoryItem item =
        LevelUpHistoryItem.builder()
            .levelUpHistoryId(99L)
            .fromLevel(1)
            .toLevel(2)
            .spentXp(100)
            .rewardMztk(5)
            .rewardStatus(RewardStatus.PENDING)
            .rewardTxStatus(RewardTxStatus.CREATED)
            .rewardTxPhase(RewardTxPhase.PENDING)
            .rewardTxHash("0xabc")
            .rewardExplorerUrl("https://explorer/tx/0xabc")
            .createdAt(createdAt)
            .build();

    assertThat(item.levelUpHistoryId()).isEqualTo(99L);
    assertThat(item.rewardTxStatus()).isEqualTo(RewardTxStatus.CREATED);
    assertThat(item.createdAt()).isEqualTo(createdAt);
  }
}
