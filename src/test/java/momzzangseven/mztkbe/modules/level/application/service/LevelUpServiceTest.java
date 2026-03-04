package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.RewardIntentCreationException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelUpHistoryPort;
import momzzangseven.mztkbe.modules.level.application.port.out.LoadActiveWalletPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.LevelUpHistory;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardStatus;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxPhase;
import momzzangseven.mztkbe.modules.level.domain.vo.RewardTxStatus;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LevelUpServiceTest {

  @Mock private UserProgressPort userProgressPort;
  @Mock private LevelPolicyResolver levelPolicyResolver;
  @Mock private LevelUpHistoryPort levelUpHistoryPort;
  @Mock private RewardMztkPort rewardMztkPort;
  @Mock private LoadActiveWalletPort loadActiveWalletPort;

  private LevelUpService service;

  @BeforeEach
  void setUp() {
    service =
        new LevelUpService(
            userProgressPort,
            levelPolicyResolver,
            levelUpHistoryPort,
            rewardMztkPort,
            loadActiveWalletPort);
  }

  @Test
  void execute_shouldThrowWhenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void execute_shouldThrowWhenWalletMissing() {
    when(loadActiveWalletPort.loadActiveWalletAddress(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(LevelUpCommand.of(1L)))
        .isInstanceOf(WalletNotConnectedException.class);
  }

  @Test
  void execute_shouldLevelUpAndReturnRewardResult() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(55L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any())).thenReturn(RewardMztkResult.success("0xabc"));

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.levelUpHistoryId()).isEqualTo(55L);
    assertThat(result.fromLevel()).isEqualTo(1);
    assertThat(result.toLevel()).isEqualTo(2);
    assertThat(result.rewardTxStatus()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.rewardStatus()).isEqualTo(RewardStatus.SUCCESS);
    verify(userProgressPort).saveUserProgress(any(UserProgress.class));
  }

  @Test
  @DisplayName("rewardMztk=0 이면 포트 호출 없이 즉시 success 반환")
  void execute_zeroReward_skipsRewardPort() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(0).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(56L, userId, 10L, 1, 2, 100, 0, now));

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.rewardMztk()).isZero();
    assertThat(result.rewardTxStatus()).isEqualTo(RewardTxStatus.SUCCEEDED);
    assertThat(result.rewardStatus()).isEqualTo(RewardStatus.SUCCESS);
    verify(rewardMztkPort, never()).reward(any());
  }

  @Test
  @DisplayName("rewardMztkPort가 null을 반환하면 CREATED(NULL_RESULT) 처리")
  void execute_nullRewardResult_returnsCreated() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(57L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any())).thenReturn(null);

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.rewardTxStatus()).isEqualTo(RewardTxStatus.CREATED);
  }

  @Test
  @DisplayName("rewardMztkPort가 BusinessException을 던지면 재throw")
  void execute_businessExceptionFromPort_rethrows() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(58L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any()))
        .thenThrow(new momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException(userId));

    assertThatThrownBy(() -> service.execute(LevelUpCommand.of(userId)))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("rewardMztkPort가 일반 Exception을 던지면 RewardIntentCreationException으로 감쌈")
  void execute_unexpectedExceptionFromPort_wrapsInRewardIntentCreationException() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(59L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any())).thenThrow(new RuntimeException("unexpected"));

    assertThatThrownBy(() -> service.execute(LevelUpCommand.of(userId)))
        .isInstanceOf(RewardIntentCreationException.class);
  }

  @Test
  @DisplayName("toLegacyRewardStatus - FAILED_ONCHAIN → RewardStatus.FAILED")
  void execute_failedOnchainStatus_returnsFailed() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(60L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any()))
        .thenReturn(new RewardMztkResult(RewardTxStatus.FAILED_ONCHAIN, "0xfail", "on-chain fail"));

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.rewardStatus()).isEqualTo(RewardStatus.FAILED);
    assertThat(result.rewardTxPhase()).isEqualTo(RewardTxPhase.FAILED);
  }

  @Test
  @DisplayName("toLegacyRewardStatus - PENDING → RewardStatus.PENDING")
  void execute_pendingStatus_returnsPending() {
    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(61L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any())).thenReturn(RewardMztkResult.pending("0xpending"));

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.rewardStatus()).isEqualTo(RewardStatus.PENDING);
    assertThat(result.rewardTxPhase()).isEqualTo(RewardTxPhase.PENDING);
  }

  @Test
  @DisplayName("buildExplorerUrl - explorerBaseUrl이 설정된 경우 URL 반환")
  void execute_withExplorerBaseUrl_returnsExplorerUrl() throws Exception {
    Field field = LevelUpService.class.getDeclaredField("explorerBaseUrl");
    field.setAccessible(true);
    field.set(service, "https://etherscan.io");

    Long userId = 1L;
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    UserProgress progress =
        UserProgress.builder()
            .userId(userId)
            .level(1)
            .availableXp(150)
            .lifetimeXp(500)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    LevelPolicy policy =
        LevelPolicy.builder().id(10L).level(1).requiredXp(100).rewardMztk(5).enabled(true).build();

    when(loadActiveWalletPort.loadActiveWalletAddress(userId))
        .thenReturn(Optional.of(EvmAddress.of("0x1111111111111111111111111111111111111111")));
    when(userProgressPort.loadOrCreateUserProgress(userId)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(userId)).thenReturn(progress);
    when(levelPolicyResolver.resolveLevelUpPolicy(eqInt(1), any(LocalDateTime.class)))
        .thenReturn(policy);
    when(levelUpHistoryPort.saveLevelUpHistory(any()))
        .thenReturn(LevelUpHistory.reconstitute(62L, userId, 10L, 1, 2, 100, 5, now));
    when(rewardMztkPort.reward(any())).thenReturn(RewardMztkResult.success("0xabc123"));

    LevelUpResult result = service.execute(LevelUpCommand.of(userId));

    assertThat(result.rewardExplorerUrl()).isEqualTo("https://etherscan.io/tx/0xabc123");
  }

  private int eqInt(int value) {
    return org.mockito.ArgumentMatchers.eq(value);
  }
}
