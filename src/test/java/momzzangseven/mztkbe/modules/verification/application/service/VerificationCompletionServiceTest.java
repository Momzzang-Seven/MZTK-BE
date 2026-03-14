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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
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
    VerificationStateTransitionService stateTransitionService =
        new VerificationStateTransitionService(verificationRequestPort);
    VerificationRewardTransactionalService rewardTransactionalService =
        new VerificationRewardTransactionalService(
            verificationRequestPort, grantXpPort, xpLedgerQueryPort, timePolicy);
    VerificationRewardService rewardService =
        new VerificationRewardService(rewardTransactionalService);
    service =
        new VerificationCompletionService(
            stateTransitionService, rewardService, xpLedgerQueryPort, timePolicy, resultFactory);
  }

  @Test
  void returnsExistingResultWhenLockedRowIsAlreadyVerified() {
    VerificationRequest verified = existingRequest("verification-1", VerificationStatus.VERIFIED);
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
  void returnsExistingResultWhenLockedRowIsAlreadyRejected() {
    VerificationRequest rejected = existingRequest("verification-2", VerificationStatus.REJECTED);
    when(verificationRequestPort.findByVerificationIdForUpdate("verification-2"))
        .thenReturn(Optional.of(rejected));

    SubmitWorkoutVerificationResult result =
        service.complete(
            1L,
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            "verification-2",
            VerificationEvaluationResult.verified(LocalDate.of(2026, 3, 13), null),
            new WorkoutPhotoVerificationPolicy());

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    verify(verificationRequestPort, never()).save(any());
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  void marksFailedWhenEvaluationFailed() {
    VerificationRequest locked =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg")
            .toAnalyzing();
    VerificationRequest failed = locked.toFailed(FailureCode.EXTERNAL_AI_TIMEOUT);
    when(verificationRequestPort.findByVerificationIdForUpdate(locked.getVerificationId()))
        .thenReturn(Optional.of(locked));
    when(verificationRequestPort.save(any())).thenReturn(failed);

    SubmitWorkoutVerificationResult result =
        service.complete(
            1L,
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            locked.getVerificationId(),
            VerificationEvaluationResult.failed(FailureCode.EXTERNAL_AI_TIMEOUT),
            new WorkoutPhotoVerificationPolicy());

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(FailureCode.EXTERNAL_AI_TIMEOUT);
    assertThat(result.completedMethod()).isNull();
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  void marksRejectedWithExifDateMismatchShortcut() {
    VerificationRequest locked =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg")
            .toAnalyzing();
    VerificationRequest rejected =
        locked.toRejected(
            RejectionReasonCode.EXIF_DATE_MISMATCH,
            "EXIF shot date must be today in KST",
            LocalDate.of(2026, 3, 12),
            LocalDateTime.of(2026, 3, 12, 23, 59));
    when(verificationRequestPort.findByVerificationIdForUpdate(locked.getVerificationId()))
        .thenReturn(Optional.of(locked));
    when(verificationRequestPort.save(any())).thenReturn(rejected);

    SubmitWorkoutVerificationResult result =
        service.complete(
            1L,
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            locked.getVerificationId(),
            VerificationEvaluationResult.rejected(
                RejectionReasonCode.EXIF_DATE_MISMATCH,
                "custom detail should be ignored",
                LocalDate.of(2026, 3, 12),
                LocalDateTime.of(2026, 3, 12, 23, 59)),
            new WorkoutPhotoVerificationPolicy());

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(result.rejectionReasonCode()).isEqualTo(RejectionReasonCode.EXIF_DATE_MISMATCH);
    assertThat(result.rejectionReasonDetail()).isEqualTo("EXIF shot date must be today in KST");
    verify(grantXpPort, never()).grantWorkoutXp(any(), any(), any(), any());
  }

  @Test
  void resolvesCompletedMethodFromLedgerWhenGrantReturnsZero() {
    VerificationRequest locked =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, "private/workout/a.jpg")
            .toAnalyzing();
    VerificationRequest verified = locked.toVerified(LocalDate.of(2026, 3, 13), null);
    VerificationRequest rewarded = verified.rewardSucceeded("workout-record-verification:ledger-created");
    when(verificationRequestPort.findByVerificationIdForUpdate(locked.getVerificationId()))
        .thenReturn(Optional.of(locked), Optional.of(verified));
    when(verificationRequestPort.save(any())).thenReturn(verified, rewarded);
    when(grantXpPort.grantWorkoutXp(any(), any(), any(), any())).thenReturn(0);
    when(xpLedgerQueryPort.findTodayWorkoutReward(1L, LocalDate.of(2026, 3, 13)))
        .thenReturn(
            new TodayRewardSnapshot(
                true,
                100,
                LocalDate.of(2026, 3, 13),
                "workout-record-verification:ledger-created"));

    SubmitWorkoutVerificationResult result =
        service.complete(
            1L,
            TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)),
            locked.getVerificationId(),
            VerificationEvaluationResult.verified(LocalDate.of(2026, 3, 13), null),
            new WorkoutPhotoVerificationPolicy());

    assertThat(result.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(result.grantedXp()).isZero();
    assertThat(result.completedMethod()).isNotNull();
  }

  @Test
  void existingResultUsesStoredRewardSourceRefWhenSnapshotIsNone() {
    VerificationRequest verified = existingRequest("verification-3", VerificationStatus.VERIFIED);

    SubmitWorkoutVerificationResult result =
        service.existingResult(1L, TodayRewardSnapshot.none(LocalDate.of(2026, 3, 13)), verified);

    assertThat(result.completedMethod()).isNotNull();
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

  private VerificationRequest existingRequest(String verificationId, VerificationStatus status) {
    return VerificationRequest.builder()
        .verificationId(verificationId)
        .userId(1L)
        .verificationKind(VerificationKind.WORKOUT_PHOTO)
        .status(status)
        .rewardStatus(
            status == VerificationStatus.VERIFIED
                ? VerificationRewardStatus.SUCCEEDED
                : VerificationRewardStatus.NOT_REQUESTED)
        .rewardSourceRef(
            status == VerificationStatus.VERIFIED
                ? "workout-photo-verification:" + verificationId
                : null)
        .tmpObjectKey("private/workout/a.jpg")
        .build();
  }
}
