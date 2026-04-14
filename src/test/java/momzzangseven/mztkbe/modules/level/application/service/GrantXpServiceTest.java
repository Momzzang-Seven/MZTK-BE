package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.out.EnsureUserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantXpServiceTest {

  @Mock private EnsureUserProgressPort ensureUserProgressPort;
  @Mock private UserProgressPort userProgressPort;
  @Mock private PolicyPort policyPort;
  @Mock private XpLedgerPort xpLedgerPort;

  private GrantXpService service;

  @BeforeEach
  void setUp() {
    service =
        new GrantXpService(
            ensureUserProgressPort,
            userProgressPort,
            policyPort,
            xpLedgerPort,
            ZoneId.of("Asia/Seoul"));
  }

  @Test
  void execute_shouldThrowWhenCommandIsNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void execute_shouldReturnAlreadyGrantedWhenIdempotencyKeyExists() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 26, 10, 0);
    GrantXpCommand command =
        GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:1:20260226", "attendance");
    UserProgress progress = baseProgress(1L, 1, 0, 0);
    XpPolicy policy = xpPolicy(XpType.CHECK_IN, 10, 5);
    LocalDate earnedOn = LocalDate.of(2026, 2, 26);

    when(ensureUserProgressPort.loadOrCreateUserProgress(1L)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(1L)).thenReturn(progress);
    when(policyPort.loadXpPolicy(XpType.CHECK_IN, occurredAt)).thenReturn(Optional.of(policy));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, earnedOn)).thenReturn(2);
    when(xpLedgerPort.existsByUserIdAndIdempotencyKey(1L, "checkin:1:20260226")).thenReturn(true);

    GrantXpResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.ALREADY_GRANTED);
    assertThat(result.grantedXp()).isZero();
    verify(xpLedgerPort, never()).trySaveXpLedger(any());
    verify(userProgressPort, never()).saveUserProgress(any());
  }

  @Test
  void execute_shouldReturnDailyCapReachedWhenCapIsZero() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 26, 10, 0);
    GrantXpCommand command =
        GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:1:20260226", "attendance");
    UserProgress progress = baseProgress(1L, 1, 0, 0);
    XpPolicy policy = xpPolicy(XpType.CHECK_IN, 10, 0);
    LocalDate earnedOn = LocalDate.of(2026, 2, 26);

    when(ensureUserProgressPort.loadOrCreateUserProgress(1L)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(1L)).thenReturn(progress);
    when(policyPort.loadXpPolicy(XpType.CHECK_IN, occurredAt)).thenReturn(Optional.of(policy));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, earnedOn)).thenReturn(0);
    when(xpLedgerPort.existsByUserIdAndIdempotencyKey(1L, "checkin:1:20260226")).thenReturn(false);

    GrantXpResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.DAILY_CAP_REACHED);
    verify(xpLedgerPort, never()).trySaveXpLedger(any());
  }

  @Test
  void execute_shouldReturnAlreadyGrantedWhenInsertRaceDetected() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 26, 10, 0);
    GrantXpCommand command =
        GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:1:20260226", "attendance");
    UserProgress progress = baseProgress(1L, 1, 0, 0);
    XpPolicy policy = xpPolicy(XpType.CHECK_IN, 10, 5);
    LocalDate earnedOn = LocalDate.of(2026, 2, 26);

    when(ensureUserProgressPort.loadOrCreateUserProgress(1L)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(1L)).thenReturn(progress);
    when(policyPort.loadXpPolicy(XpType.CHECK_IN, occurredAt)).thenReturn(Optional.of(policy));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, earnedOn)).thenReturn(1);
    when(xpLedgerPort.existsByUserIdAndIdempotencyKey(1L, "checkin:1:20260226")).thenReturn(false);
    when(xpLedgerPort.trySaveXpLedger(any())).thenReturn(false);

    GrantXpResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.ALREADY_GRANTED);
    verify(userProgressPort, never()).saveUserProgress(any());
  }

  @Test
  void execute_shouldReturnDailyCapReachedWhenGrantedTodayReachesPositiveCap() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 26, 10, 0);
    GrantXpCommand command =
        GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:1:20260226", "attendance");
    UserProgress progress = baseProgress(1L, 1, 0, 0);
    XpPolicy policy = xpPolicy(XpType.CHECK_IN, 10, 3);
    LocalDate earnedOn = LocalDate.of(2026, 2, 26);

    when(ensureUserProgressPort.loadOrCreateUserProgress(1L)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(1L)).thenReturn(progress);
    when(policyPort.loadXpPolicy(XpType.CHECK_IN, occurredAt)).thenReturn(Optional.of(policy));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, earnedOn)).thenReturn(3);
    when(xpLedgerPort.existsByUserIdAndIdempotencyKey(1L, "checkin:1:20260226")).thenReturn(false);

    GrantXpResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.DAILY_CAP_REACHED);
    verify(xpLedgerPort, never()).trySaveXpLedger(any());
  }

  @Test
  void execute_shouldGrantXpAndSaveProgressWhenAllChecksPass() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 26, 10, 0);
    GrantXpCommand command =
        GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:1:20260226", "attendance");
    UserProgress progress = baseProgress(1L, 1, 10, 100);
    XpPolicy policy = xpPolicy(XpType.CHECK_IN, 20, 5);
    LocalDate earnedOn = LocalDate.of(2026, 2, 26);

    when(ensureUserProgressPort.loadOrCreateUserProgress(1L)).thenReturn(progress);
    when(userProgressPort.loadUserProgressWithLock(1L)).thenReturn(progress);
    when(policyPort.loadXpPolicy(XpType.CHECK_IN, occurredAt)).thenReturn(Optional.of(policy));
    when(xpLedgerPort.countByUserIdAndTypeAndEarnedOn(1L, XpType.CHECK_IN, earnedOn)).thenReturn(1);
    when(xpLedgerPort.existsByUserIdAndIdempotencyKey(1L, "checkin:1:20260226")).thenReturn(false);
    when(xpLedgerPort.trySaveXpLedger(any())).thenReturn(true);

    GrantXpResult result = service.execute(command);

    assertThat(result.status()).isEqualTo(GrantXpResult.Status.GRANTED);
    assertThat(result.grantedXp()).isEqualTo(20);
    assertThat(result.grantedCountToday()).isEqualTo(2);

    ArgumentCaptor<UserProgress> progressCaptor = ArgumentCaptor.forClass(UserProgress.class);
    verify(userProgressPort).saveUserProgress(progressCaptor.capture());
    UserProgress saved = progressCaptor.getValue();
    assertThat(saved.getAvailableXp()).isEqualTo(30);
    assertThat(saved.getLifetimeXp()).isEqualTo(120);
  }

  private XpPolicy xpPolicy(XpType type, int xpAmount, int dailyCap) {
    return XpPolicy.builder()
        .id(1L)
        .type(type)
        .xpAmount(xpAmount)
        .dailyCap(dailyCap)
        .enabled(true)
        .build();
  }

  private UserProgress baseProgress(Long userId, int level, int availableXp, int lifetimeXp) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 26, 0, 0);
    return UserProgress.builder()
        .userId(userId)
        .level(level)
        .availableXp(availableXp)
        .lifetimeXp(lifetimeXp)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
