package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodayWorkoutCompletionQueryServiceTest {

  @Mock private XpLedgerQueryPort xpLedgerQueryPort;
  @Mock private VerificationRequestPort verificationRequestPort;

  private TodayWorkoutCompletionQueryService service;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy policy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    service =
        new TodayWorkoutCompletionQueryService(xpLedgerQueryPort, verificationRequestPort, policy);
  }

  @Test
  void returnsTodayCompletionSnapshot() {
    LocalDate today = LocalDate.of(2026, 3, 13);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, today))
        .thenReturn(new TodayRewardSnapshot(true, 100, today, "workout-photo-verification:abc"));
    VerificationRequest latestRequest =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_RECORD, "private/workout/a.jpg")
            .toRejected(
                RejectionReasonCode.DATE_MISMATCH,
                "exercise date must be today",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 50));
    when(verificationRequestPort.findLatestUpdatedToday(1L, today))
        .thenReturn(Optional.of(latestRequest));

    TodayWorkoutCompletionResult result = service.execute(1L);

    assertThat(result.todayCompleted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.completedMethod().name()).isEqualTo("WORKOUT_PHOTO");
    assertThat(result.rewardGrantedToday()).isTrue();
    assertThat(result.earnedDate()).isEqualTo(today);
    assertThat(result.latestVerification()).isNotNull();
    assertThat(result.latestVerification().verificationId())
        .isEqualTo(latestRequest.getVerificationId());
    assertThat(result.latestVerification().verificationKind())
        .isEqualTo(VerificationKind.WORKOUT_RECORD);
    assertThat(result.latestVerification().verificationStatus())
        .isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.latestVerification().rejectionReasonCode())
        .isEqualTo(RejectionReasonCode.DATE_MISMATCH);
  }

  @Test
  void returnsIncompleteSnapshotWhenRewardAndLatestVerificationAreMissing() {
    LocalDate today = LocalDate.of(2026, 3, 13);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, today))
        .thenReturn(TodayRewardSnapshot.none(today));
    when(verificationRequestPort.findLatestUpdatedToday(1L, today)).thenReturn(Optional.empty());

    TodayWorkoutCompletionResult result = service.execute(1L);

    assertThat(result.todayCompleted()).isFalse();
    assertThat(result.completedMethod()).isEqualTo(CompletedMethod.UNKNOWN);
    assertThat(result.rewardGrantedToday()).isFalse();
    assertThat(result.grantedXp()).isZero();
    assertThat(result.earnedDate()).isEqualTo(today);
    assertThat(result.latestVerification()).isNull();
  }
}
