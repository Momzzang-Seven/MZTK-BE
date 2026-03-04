package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpHistoryItem;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadLevelRewardTransactionPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
  @DisplayName("입력 유효성 검증 - null userId, 음수 page, size 범위 초과")
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
  @DisplayName("size > MAX_PAGE_SIZE(100) 이면 예외")
  void execute_sizeOverMaxPageSize_throws() {
    assertThatThrownBy(() -> service.execute(1L, 0, 101))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be between");
  }

  @Test
  @DisplayName("hasNext=true 일 때 결과 트리밍 및 rewardTxView 매핑")
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

  @Test
  @DisplayName("hasNext=false - 정확한 페이지 크기로 반환 시 트리밍 없음")
  void execute_hasNextFalse_returnsAllWithoutTrimming() {
    LevelUpHistory h = history(20L, 1, 2, 100, 5);
    when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
    when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(20L)))
        .thenReturn(Collections.emptyMap());

    GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

    assertThat(result.hasNext()).isFalse();
    assertThat(result.histories()).hasSize(1);
  }

  @Nested
  @DisplayName("resolveRewardTxStatus() - rewardTxView=null 분기")
  class ResolveRewardTxStatusBranches {

    @Test
    @DisplayName("rewardTxView=null & rewardMztk<=0 → SUCCEEDED")
    void noTxView_zeroReward_returnsSucceeded() {
      LevelUpHistory h = history(30L, 1, 2, 100, 0); // rewardMztk=0
      when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
      when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(30L)))
          .thenReturn(Collections.emptyMap()); // no tx view

      GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

      LevelUpHistoryItem item = result.histories().getFirst();
      assertThat(item.rewardTxStatus()).isEqualTo(RewardTxStatus.SUCCEEDED);
      assertThat(item.rewardStatus()).isEqualTo(RewardStatus.SUCCESS);
      assertThat(item.rewardTxHash()).isNull();
    }

    @Test
    @DisplayName("rewardTxView=null & rewardMztk>0 → CREATED")
    void noTxView_positiveReward_returnsCreated() {
      LevelUpHistory h = history(31L, 1, 2, 100, 10); // rewardMztk=10
      when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
      when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(31L)))
          .thenReturn(Collections.emptyMap()); // no tx view

      GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

      LevelUpHistoryItem item = result.histories().getFirst();
      assertThat(item.rewardTxStatus()).isEqualTo(RewardTxStatus.CREATED);
      assertThat(item.rewardStatus()).isEqualTo(RewardStatus.PENDING);
    }
  }

  @Nested
  @DisplayName("toLegacyStatus() 분기")
  class ToLegacyStatusBranches {

    @Test
    @DisplayName("FAILED_ONCHAIN → RewardStatus.FAILED")
    void failedOnchain_returnsFailed() {
      LevelUpHistory h = history(40L, 1, 2, 100, 10);
      when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
      when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(40L)))
          .thenReturn(
              Map.of(40L, new LoadLevelRewardTransactionPort.RewardTxView(
                  RewardTxStatus.FAILED_ONCHAIN, "0xfail")));

      GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

      assertThat(result.histories().getFirst().rewardStatus()).isEqualTo(RewardStatus.FAILED);
      assertThat(result.histories().getFirst().rewardTxPhase()).isEqualTo(RewardTxPhase.FAILED);
    }

    @Test
    @DisplayName("PENDING(진행중) → RewardStatus.PENDING")
    void pendingStatus_returnsPending() {
      LevelUpHistory h = history(41L, 1, 2, 100, 10);
      when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
      when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(41L)))
          .thenReturn(
              Map.of(41L, new LoadLevelRewardTransactionPort.RewardTxView(
                  RewardTxStatus.PENDING, "0xpending")));

      GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

      assertThat(result.histories().getFirst().rewardStatus()).isEqualTo(RewardStatus.PENDING);
      assertThat(result.histories().getFirst().rewardTxPhase()).isEqualTo(RewardTxPhase.PENDING);
    }
  }

  @Test
  @DisplayName("buildExplorerUrl - explorerBaseUrl이 설정되어 있으면 URL 반환")
  void buildExplorerUrl_withConfiguredBaseUrl_returnsUrl() throws Exception {
    Field field = GetMyLevelUpHistoriesService.class.getDeclaredField("explorerBaseUrl");
    field.setAccessible(true);
    field.set(service, "https://etherscan.io");

    LevelUpHistory h = history(50L, 1, 2, 100, 10);
    when(levelUpHistoryPort.loadLevelUpHistories(1L, 0, 10)).thenReturn(List.of(h));
    when(loadLevelRewardTransactionPort.loadByLevelUpHistoryIds(List.of(50L)))
        .thenReturn(
            Map.of(50L, new LoadLevelRewardTransactionPort.RewardTxView(
                RewardTxStatus.SUCCEEDED, "0xabc123")));

    GetMyLevelUpHistoriesResult result = service.execute(1L, 0, 10);

    assertThat(result.histories().getFirst().rewardExplorerUrl())
        .isEqualTo("https://etherscan.io/tx/0xabc123");
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
