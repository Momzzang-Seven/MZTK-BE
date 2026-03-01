package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMyLevelUpHistoriesServiceTest {

  @Mock private LevelUpHistoryPort levelUpHistoryPort;
  @Mock private LoadLevelRewardTransactionPort loadLevelRewardTransactionPort;

  private GetMyLevelUpHistoriesService service;

  @BeforeEach
  void setUp() {
    service = new GetMyLevelUpHistoriesService(levelUpHistoryPort, loadLevelRewardTransactionPort);
  }

  @Test
  void execute_shouldValidateInputs() {
    assertThatThrownBy(() -> service.execute(null, 0, 10))
        .isInstanceOf(UserNotAuthenticatedException.class);
    assertThatThrownBy(() -> service.execute(1L, -1, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("page must be >= 0");
    assertThatThrownBy(() -> service.execute(1L, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be between");
  }

  @Test
  void execute_shouldTrimAndMapRewardTransactionInfo() {
    LevelUpHistory first = history(10L, 1, 2, 100, 10);
    LevelUpHistory second = history(11L, 2, 3, 200, 0);

    when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 1)).thenReturn(List.of(first, second));
    when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(10L)))
        .thenReturn(
            Map.of(
                10L,
                new LoadLevelRewardTransactionPort.RewardTxView(
                    RewardTxStatus.SUCCEEDED, "0xabc")));

    GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 1);

    assertThat(result.hasNext()).isTrue();
    assertThat(result.histories()).hasSize(1);
    assertThat(result.histories().getFirst().rewardTxStatus()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.histories().getFirst().rewardStatus()).isEqualTo(RewardStatus.SUCCESS);
    assertThat(result.histories().getFirst().rewardExplorerUrl()).isNull();
  }

  private LevelUpHistory history(Long id, int fromLevel, int toLevel, int spentXp, int rewardMztk) {
    return LevelUpHistory.reconstitute(
        id,
        1L,
        100L,
        fromLevel,
        toLevel,
        spentXp,
        rewardMztk,
        LocalDateTime.of(2026, 2, 28, 12, 0));
  }
}
