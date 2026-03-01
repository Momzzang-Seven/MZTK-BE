package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.Test;

class GetMyLevelUpHistoriesResultTest {

  @Test
  void builder_shouldSetPageAndItems() {
    LevelUpHistoryItem item =
        LevelUpHistoryItem.builder()
            .levelUpHistoryId(10L)
            .fromLevel(1)
            .toLevel(2)
            .spentXp(100)
            .rewardMztk(5)
            .rewardStatus(RewardStatus.SUCCESS)
            .rewardTxStatus(RewardTxStatus.SUCCEEDED)
            .rewardTxPhase(RewardTxPhase.SUCCESS)
            .rewardTxHash("0xabc")
            .createdAt(LocalDateTime.of(2026, 2, 28, 10, 0))
            .build();

    GetMyLevelUpHistoriesResult result =
        GetMyLevelUpHistoriesResult.builder()
            .page(1)
            .size(20)
            .hasNext(true)
            .histories(List.of(item))
            .build();

    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.histories()).containsExactly(item);
  }
}
