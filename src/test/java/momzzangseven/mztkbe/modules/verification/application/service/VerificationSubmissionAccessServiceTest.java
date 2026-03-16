package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationReservation;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VerificationSubmissionAccessServiceTest {

  @Mock private VerificationRequestPort verificationRequestPort;
  @Mock private WorkoutUploadLookupPort workoutUploadLookupPort;
  @Mock private ObjectStoragePort objectStoragePort;
  @Mock private VerificationSubmissionValidator verificationSubmissionValidator;

  private VerificationSubmissionAccessService service;

  @BeforeEach
  void setUp() {
    VerificationTimePolicy timePolicy =
        new VerificationTimePolicy(
            ZoneId.of("Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    service =
        new VerificationSubmissionAccessService(
            verificationRequestPort,
            workoutUploadLookupPort,
            objectStoragePort,
            verificationSubmissionValidator,
            timePolicy);
  }

  @Test
  void reserveNewReturnsExistingWhenDuplicateInsertDetected() {
    SubmitWorkoutVerificationCommand command = command("private/workout/race.jpg");
    VerificationRequest existing =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, command.tmpObjectKey());
    stubLockedUpload(command);
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.save(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    VerificationReservation reservation = service.reserveNew(command);

    assertThat(reservation.readyForAnalysis()).isFalse();
    assertThat(reservation.request().getVerificationId()).isEqualTo(existing.getVerificationId());
  }

  @Test
  void reserveNewThrowsWhenDuplicateDetectedButExistingRowCannotBeFound() {
    SubmitWorkoutVerificationCommand command = command("private/workout/race-missing.jpg");
    stubLockedUpload(command);
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty(), Optional.empty());
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.save(any()))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> service.reserveNew(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("verification row must exist after duplicate tmpObjectKey");
  }

  @Test
  void reserveNewReturnsExistingWhenTransitionedRowIsNotAnalyzing() {
    SubmitWorkoutVerificationCommand command = command("private/workout/already-verified.jpg");
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, command.tmpObjectKey());
    VerificationRequest verified =
        pending.toVerified(LocalDateTime.of(2026, 3, 13, 9, 0).toLocalDate(), null);
    stubLockedUpload(command);
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.save(any())).thenReturn(pending);
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(verified));

    VerificationReservation reservation = service.reserveNew(command);

    assertThat(reservation.readyForAnalysis()).isFalse();
    assertThat(reservation.request().getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    verify(verificationRequestPort, never()).save(verified.beginAnalysis());
  }

  @Test
  void reserveNewThrowsWhenTransitionTargetCannotBeLocked() {
    SubmitWorkoutVerificationCommand command = command("private/workout/missing-transition.jpg");
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, command.tmpObjectKey());
    stubLockedUpload(command);
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.save(any())).thenReturn(pending);
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.reserveNew(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("verification row must exist before analysis");
  }

  @Test
  void reserveNewTransitionsFailedRowToAnalyzing() {
    SubmitWorkoutVerificationCommand command = command("private/workout/failed-row.jpg");
    VerificationRequest pending =
        VerificationRequest.newPending(1L, VerificationKind.WORKOUT_PHOTO, command.tmpObjectKey());
    VerificationRequest failed =
        VerificationRequest.builder()
            .id(1L)
            .verificationId(pending.getVerificationId())
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.FAILED)
            .tmpObjectKey(command.tmpObjectKey())
            .createdAt(Instant.parse("2026-03-13T00:10:00Z"))
            .build();
    VerificationRequest analyzing = failed.beginAnalysis();

    stubLockedUpload(command);
    when(verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(Optional.empty());
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.save(any())).thenReturn(pending, analyzing);
    when(verificationRequestPort.findByVerificationIdForUpdate(pending.getVerificationId()))
        .thenReturn(Optional.of(failed));

    VerificationReservation reservation = service.reserveNew(command);

    assertThat(reservation.readyForAnalysis()).isTrue();
    assertThat(reservation.request().getStatus()).isEqualTo(VerificationStatus.ANALYZING);
  }

  @Test
  void reserveRetryReturnsExistingWhenRowIsNotRetryable() {
    SubmitWorkoutVerificationCommand command = command("private/workout/retry.jpg");
    VerificationRequest existing =
        VerificationRequest.builder()
            .verificationId("verification-1")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.REJECTED)
            .tmpObjectKey(command.tmpObjectKey())
            .build();
    stubLockedUpload(command);
    when(objectStoragePort.exists(command.tmpObjectKey())).thenReturn(true);
    when(verificationRequestPort.findByVerificationIdForUpdate(existing.getVerificationId()))
        .thenReturn(Optional.of(existing));

    VerificationReservation reservation = service.reserveRetry(command, existing);

    assertThat(reservation.readyForAnalysis()).isFalse();
    assertThat(reservation.request().getStatus()).isEqualTo(VerificationStatus.REJECTED);
  }

  @Test
  void retryableFailedTodayRequiresCreatedAt() {
    VerificationRequest failedWithoutCreatedAt =
        VerificationRequest.builder()
            .verificationId("verification-2")
            .userId(1L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO)
            .status(VerificationStatus.FAILED)
            .tmpObjectKey("private/workout/a.jpg")
            .createdAt(null)
            .build();

    assertThat(service.isRetryableFailedToday(failedWithoutCreatedAt)).isFalse();
  }

  private SubmitWorkoutVerificationCommand command(String key) {
    return new SubmitWorkoutVerificationCommand(1L, key, VerificationKind.WORKOUT_PHOTO);
  }

  private void stubLockedUpload(SubmitWorkoutVerificationCommand command) {
    when(workoutUploadLookupPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()))
        .thenReturn(
            Optional.of(
                new WorkoutUploadReference(
                    command.userId(), command.tmpObjectKey(), command.tmpObjectKey())));
  }
}
