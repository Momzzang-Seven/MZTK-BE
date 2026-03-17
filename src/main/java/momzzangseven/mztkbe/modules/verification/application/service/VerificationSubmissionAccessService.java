package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.verification.VerificationUploadNotFoundException;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationReservation;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationSubmissionAccessService {

  private final VerificationRequestPort verificationRequestPort;
  private final WorkoutUploadLookupPort workoutUploadLookupPort;
  private final ObjectStoragePort objectStoragePort;
  private final VerificationSubmissionValidator verificationSubmissionValidator;
  private final VerificationTimePolicy verificationTimePolicy;

  @Transactional
  public VerificationReservation reserveNew(SubmitWorkoutVerificationCommand command) {
    WorkoutUploadReference lockedUpload = loadLockedUpload(command);
    VerificationRequest lockedExisting =
        verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey()).orElse(null);
    if (lockedExisting != null) {
      return VerificationReservation.existing(lockedExisting);
    }
    ensureObjectExists(lockedUpload.readObjectKey());

    VerificationCreationResult creation = createPendingOrReuseExisting(command);
    if (!creation.createdNew()) {
      return VerificationReservation.existing(creation.request());
    }

    VerificationRequest analyzing = transitionToAnalyzing(creation.request().getVerificationId());
    if (analyzing.getStatus() != VerificationStatus.ANALYZING) {
      return VerificationReservation.existing(analyzing);
    }
    return VerificationReservation.analyzing(analyzing, lockedUpload);
  }

  @Transactional
  public VerificationReservation reserveRetry(
      SubmitWorkoutVerificationCommand command, VerificationRequest existing) {
    WorkoutUploadReference lockedUpload = loadLockedUpload(command);
    ensureObjectExists(lockedUpload.readObjectKey());

    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(existing.getVerificationId())
            .orElse(existing);
    if (!isRetryableFailedToday(locked)) {
      return VerificationReservation.existing(locked);
    }

    VerificationRequest analyzing = verificationRequestPort.save(locked.beginAnalysis());
    return VerificationReservation.analyzing(analyzing, lockedUpload);
  }

  public boolean isRetryableFailedToday(VerificationRequest request) {
    return request.getStatus() == VerificationStatus.FAILED
        && request.getCreatedAt() != null
        && verificationTimePolicy.isToday(request.getCreatedAt());
  }

  private WorkoutUploadReference loadLockedUpload(SubmitWorkoutVerificationCommand command) {
    WorkoutUploadReference lockedUpload =
        workoutUploadLookupPort
            .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
            .orElseThrow(VerificationUploadNotFoundException::new);
    verificationSubmissionValidator.validateUploadOwnership(command.userId(), lockedUpload);
    return lockedUpload;
  }

  private void ensureObjectExists(String readObjectKey) {
    if (!objectStoragePort.exists(readObjectKey)) {
      throw new VerificationUploadNotFoundException();
    }
  }

  private VerificationCreationResult createPendingOrReuseExisting(
      SubmitWorkoutVerificationCommand command) {
    try {
      return new VerificationCreationResult(
          verificationRequestPort.save(
              VerificationRequest.newPending(
                  command.userId(), command.kind(), command.tmpObjectKey())),
          true);
    } catch (DataIntegrityViolationException ex) {
      return new VerificationCreationResult(
          verificationRequestPort
              .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "verification row must exist after duplicate tmpObjectKey", ex)),
          false);
    }
  }

  private VerificationRequest transitionToAnalyzing(String verificationId) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () -> new IllegalStateException("verification row must exist before analysis"));
    if (locked.getStatus() == VerificationStatus.PENDING
        || locked.getStatus() == VerificationStatus.FAILED) {
      return verificationRequestPort.save(locked.beginAnalysis());
    }
    return locked;
  }

  private record VerificationCreationResult(VerificationRequest request, boolean createdNew) {}
}
