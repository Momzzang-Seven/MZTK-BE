package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationCompletionServiceTest {

  @Mock private VerificationRequestPort verificationRequestPort;
  @Mock private GrantXpPort grantXpPort;
  @Mock private XpLedgerQueryPort xpLedgerQueryPort;

  private VerificationCompletionService service;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    VerificationSubmissionResultFactory resultFactory =
        new VerificationSubmissionResultFactory(timePolicy);
    service =
        new VerificationCompletionService(
            verificationRequestPort, grantXpPort, xpLedgerQueryPort, timePolicy, resultFactory);
  }

  @Test
  void returnsExistingResultWhenLockedRowIsAlreadyVerified() {
    VerificationRequest verified =
        VerificationRequest.builder()
            .verificationId("verification-1")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.VERIFIED)
            .tmpObjectKey("private/workout/a.jpg")
            .build();
    when(verificationRequestPort.findByVerificationIdForUpdate("verification-1"))
        .thenReturn(Optional.of(verified));

    var result =
        service.complete(
            1L,
            new TodayRewardSnapshot(
                true, 100, LocalDate.of(2026, 3, 13), "workout-photo-verification:verification-1"),
            "verification-1",
            VerificationEvaluationResult.verified(null, null),
            new WorkoutPhotoVerificationPolicy());

    assertThat(result.verificationId()).isEqualTo("verification-1");
    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    verify(verificationRequestPort, never()).save(any());
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  void throwsWhenVerificationRowCannotBeLockedBeforeCompletion() {
    when(verificationRequestPort.findByVerificationIdForUpdate("missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.complete(
                    1L,
                    TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
                    "missing",
                    VerificationEvaluationResult.verified(LocalDate.of(2026, 3, 13), null),
                    new WorkoutRecordVerificationPolicy()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("before completion");
  }
}
