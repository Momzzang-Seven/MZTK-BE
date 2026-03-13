package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
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
    when(verificationRequestPort.findLatestUpdatedToday(1L, today))
        .thenReturn(
            Optional.of(
                VerificationRequest.newPending(
                    1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg")));

    TodayWorkoutCompletionResult result = service.execute(1L);

    assertThat(result.todayCompleted()).isTrue();
    assertThat(result.grantedXp()).isEqualTo(100);
    assertThat(result.completedMethod().name()).isEqualTo("WORKOUT_PHOTO");
  }
}
