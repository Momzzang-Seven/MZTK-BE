package momzzangseven.mztkbe.modules.verification.application.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.verification.InvalidTmpObjectKeyException;
import momzzangseven.mztkbe.global.error.verification.InvalidVerificationImageExtensionException;
import momzzangseven.mztkbe.global.error.verification.VerificationAlreadyCompletedTodayException;
import momzzangseven.mztkbe.global.error.verification.VerificationKindMismatchException;
import momzzangseven.mztkbe.global.error.verification.VerificationUploadForbiddenException;
import momzzangseven.mztkbe.global.error.verification.VerificationUploadNotFoundException;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutUploadLookupPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional
public abstract class AbstractSubmitWorkoutVerificationService {

  protected final VerificationRequestPort verificationRequestPort;
  protected final WorkoutUploadLookupPort workoutUploadLookupPort;
  protected final ObjectStoragePort objectStoragePort;
  protected final PrepareOriginalImagePort prepareOriginalImagePort;
  protected final PrepareAnalysisImagePort prepareAnalysisImagePort;
  protected final ExifMetadataPort exifMetadataPort;
  protected final WorkoutImageAiPort workoutImageAiPort;
  protected final GrantXpPort grantXpPort;
  protected final XpLedgerQueryPort xpLedgerQueryPort;
  protected final ImageCodecSupportPort imageCodecSupportPort;
  protected final VerificationTimePolicy verificationTimePolicy;
  protected final VerificationImagePolicy verificationImagePolicy;

  public SubmitWorkoutVerificationResult execute(SubmitWorkoutVerificationCommand command) {
    LocalDate today = verificationTimePolicy.today();
    TodayRewardSnapshot todayReward =
        xpLedgerQueryPort.findTodayWorkoutReward(command.userId(), today);
    if (todayReward.rewarded()) {
      throw new VerificationAlreadyCompletedTodayException(
          verificationTimePolicy.deriveCompletedMethod(todayReward.sourceRef()),
          todayReward.earnedDate());
    }
    Optional<VerificationRequest> existingOpt =
        verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey());
    if (existingOpt.isPresent()) {
      return handleExisting(command, todayReward, existingOpt.get());
    }

    validateSubmitInput(command.tmpObjectKey());

    WorkoutUploadReference lockedUpload =
        workoutUploadLookupPort
            .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
            .orElseThrow(VerificationUploadNotFoundException::new);
    validateUploadOwnership(command.userId(), lockedUpload);

    Optional<VerificationRequest> lockedExistingOpt =
        verificationRequestPort.findByTmpObjectKeyForUpdate(command.tmpObjectKey());
    if (lockedExistingOpt.isPresent()) {
      return handleExisting(command, todayReward, lockedExistingOpt.get());
    }
    ensureObjectExists(lockedUpload.readObjectKey());

    VerificationRequest pending;
    try {
      pending =
          verificationRequestPort.save(
              VerificationRequest.newPending(
                  command.userId(), command.kind(), command.tmpObjectKey()));
    } catch (DataIntegrityViolationException ex) {
      VerificationRequest existing =
          verificationRequestPort
              .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "verification row must exist after duplicate tmpObjectKey", ex));
      TodayRewardSnapshot refreshedTodayReward =
          xpLedgerQueryPort.findTodayWorkoutReward(command.userId(), today);
      return handleExisting(command, refreshedTodayReward, existing);
    }
    VerificationRequest analyzing = lockAndTransitionToAnalyzing(pending.getVerificationId());
    if (analyzing.getStatus() != VerificationStatus.ANALYZING) {
      return mapResult(
          analyzing, 0, resolveCompletedMethodSourceRef(command.userId(), todayReward, analyzing));
    }
    return analyzeAndComplete(command, analyzing, lockedUpload);
  }

  private SubmitWorkoutVerificationResult handleExisting(
      SubmitWorkoutVerificationCommand command,
      TodayRewardSnapshot todayReward,
      VerificationRequest existing) {
    if (existing.getVerificationKind() != command.kind()) {
      throw new VerificationKindMismatchException();
    }
    if (!isRetryableFailedToday(existing)) {
      return mapResult(
          existing, 0, resolveCompletedMethodSourceRef(command.userId(), todayReward, existing));
    }
    validateSubmitInput(command.tmpObjectKey());
    return retryFailed(command, existing);
  }

  private SubmitWorkoutVerificationResult retryFailed(
      SubmitWorkoutVerificationCommand command, VerificationRequest existing) {
    WorkoutUploadReference lockedUpload =
        workoutUploadLookupPort
            .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
            .orElseThrow(VerificationUploadNotFoundException::new);
    validateUploadOwnership(command.userId(), lockedUpload);
    ensureObjectExists(lockedUpload.readObjectKey());

    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(existing.getVerificationId())
            .orElse(existing);
    if (!isRetryableFailedToday(locked)) {
      TodayRewardSnapshot refreshedTodayReward =
          xpLedgerQueryPort.findTodayWorkoutReward(
              command.userId(), verificationTimePolicy.today());
      return mapResult(
          locked,
          0,
          resolveCompletedMethodSourceRef(command.userId(), refreshedTodayReward, locked));
    }
    VerificationRequest analyzing = verificationRequestPort.save(locked.toAnalyzing());
    return analyzeAndComplete(command, analyzing, lockedUpload);
  }

  private SubmitWorkoutVerificationResult analyzeAndComplete(
      SubmitWorkoutVerificationCommand command,
      VerificationRequest analyzing,
      WorkoutUploadReference validatedUpload) {
    String extension = extractExtension(command.tmpObjectKey());
    Optional<ExifMetadataInfo> exif = Optional.empty();
    try {
      if (needsExifValidation()) {
        exif = extractExif(validatedUpload.readObjectKey(), extension);
        if (exif.isEmpty()) {
          VerificationRequest rejected =
              verificationRequestPort.save(
                  analyzing.toRejected(
                      RejectionReasonCode.MISSING_EXIF_METADATA,
                      "EXIF metadata is required",
                      null,
                      null));
          return mapResult(rejected, 0, null);
        }
        if (!verificationTimePolicy.isToday(exif.get().shotAtKst().toLocalDate())) {
          VerificationRequest rejected =
              verificationRequestPort.save(
                  analyzing.toRejected(
                      RejectionReasonCode.EXIF_DATE_MISMATCH,
                      "EXIF shot date must be today in KST",
                      exif.get().shotAtKst().toLocalDate(),
                      exif.get().shotAtKst()));
          return mapResult(rejected, 0, null);
        }
      }

      try (PreparedOriginalImage originalImage =
          prepareOriginalImagePort.prepare(validatedUpload.readObjectKey(), extension)) {
        try (PreparedAnalysisImage analysisImage =
            prepareAnalysisImagePort.prepare(
                originalImage.path(), analysisMaxLongEdge(), analysisWebpQuality())) {
          AiVerificationDecision decision = analyzeWithAi(analysisImage.path());
          if (!decision.approved()) {
            if (decision.rejectionReasonCode() == null) {
              return fail(analyzing, FailureCode.AI_RESPONSE_SCHEMA_INVALID);
            }
            VerificationRequest rejected =
                verificationRequestPort.save(
                    analyzing.toRejected(
                        decision.rejectionReasonCode(),
                        decision.rejectionReasonDetail(),
                        decision.exerciseDate(),
                        exif.map(ExifMetadataInfo::shotAtKst).orElse(null)));
            return mapResult(rejected, 0, null);
          }

          LocalDate exerciseDate = resolveVerifiedExerciseDate(decision);
          VerificationRequest verified =
              verificationRequestPort.save(
                  analyzing.toVerified(
                      exerciseDate, exif.map(ExifMetadataInfo::shotAtKst).orElse(null)));
          String sourceRef = sourceRefPrefix() + verified.getVerificationId();
          int grantedXp =
              grantXpPort.grantWorkoutXp(
                  command.userId(),
                  verified.getVerificationKind(),
                  verified.getVerificationId(),
                  sourceRef);
          String completedMethodSourceRef = sourceRef;
          if (grantedXp == 0) {
            TodayRewardSnapshot rewardSnapshot =
                xpLedgerQueryPort.findTodayWorkoutReward(
                    command.userId(), verificationTimePolicy.today());
            completedMethodSourceRef = rewardSnapshot.sourceRef();
          }
          return mapResult(verified, grantedXp, completedMethodSourceRef);
        } catch (IOException ioException) {
          return fail(analyzing, FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
        } catch (AiTimeoutException ex) {
          return fail(analyzing, FailureCode.EXTERNAL_AI_TIMEOUT);
        } catch (AiMalformedResponseException ex) {
          return fail(analyzing, FailureCode.EXTERNAL_AI_MALFORMED_RESPONSE);
        } catch (AiResponseSchemaInvalidException ex) {
          return fail(analyzing, FailureCode.AI_RESPONSE_SCHEMA_INVALID);
        } catch (AiUnavailableException ex) {
          return fail(analyzing, FailureCode.EXTERNAL_AI_UNAVAILABLE);
        } catch (RuntimeException ex) {
          return fail(analyzing, FailureCode.EXTERNAL_AI_UNAVAILABLE);
        }
      }
    } catch (IOException | RuntimeException ex) {
      VerificationRequest failed =
          verificationRequestPort.save(analyzing.toFailed(FailureCode.ORIGINAL_IMAGE_READ_FAILED));
      return mapResult(failed, 0, null);
    }
  }

  private Optional<ExifMetadataInfo> extractExif(String objectKey, String extension)
      throws IOException {
    try (StorageObjectStream objectStream = openValidatedObjectStream(objectKey, extension)) {
      return exifMetadataPort.extract(objectStream.stream());
    }
  }

  private StorageObjectStream openValidatedObjectStream(String objectKey, String extension)
      throws IOException {
    StorageObjectStream objectStream = objectStoragePort.openStream(objectKey);
    try {
      verificationImagePolicy.validateObjectMetadata(
          objectStream.contentLength(), objectStream.contentType(), extension);
      return objectStream;
    } catch (RuntimeException ex) {
      try {
        objectStream.close();
      } catch (IOException closeEx) {
        log.warn("Failed to close verification object stream: {}", objectKey, closeEx);
      }
      throw new IOException("Stored object violates image policy", ex);
    }
  }

  private VerificationRequest lockAndTransitionToAnalyzing(String verificationId) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () -> new IllegalStateException("verification row must exist before analysis"));
    if (locked.getStatus() == VerificationStatus.PENDING
        || locked.getStatus() == VerificationStatus.FAILED) {
      return verificationRequestPort.save(locked.toAnalyzing());
    }
    return locked;
  }

  private SubmitWorkoutVerificationResult fail(
      VerificationRequest analyzing, FailureCode failureCode) {
    VerificationRequest failed = verificationRequestPort.save(analyzing.toFailed(failureCode));
    return mapResult(failed, 0, null);
  }

  private String resolveCompletedMethodSourceRef(
      Long userId, TodayRewardSnapshot todayReward, VerificationRequest request) {
    if (request.getStatus() != VerificationStatus.VERIFIED) {
      return null;
    }
    if (todayReward.rewarded()) {
      return todayReward.sourceRef();
    }
    return xpLedgerQueryPort
        .findTodayWorkoutReward(userId, verificationTimePolicy.today())
        .sourceRef();
  }

  private SubmitWorkoutVerificationResult mapResult(
      VerificationRequest request, int grantedXp, String sourceRef) {
    return SubmitWorkoutVerificationResult.builder()
        .verificationId(request.getVerificationId())
        .verificationKind(request.getVerificationKind())
        .verificationStatus(request.getStatus())
        .exerciseDate(exposedExerciseDate(request))
        .completionStatus(
            request.getStatus() == VerificationStatus.VERIFIED
                ? CompletionStatus.COMPLETED
                : CompletionStatus.NOT_COMPLETED)
        .grantedXp(grantedXp)
        .completedMethod(resolveCompletedMethod(sourceRef))
        .rejectionReasonCode(request.getRejectionReasonCode())
        .rejectionReasonDetail(request.getRejectionReasonDetail())
        .failureCode(request.getFailureCode())
        .build();
  }

  private void validateSubmitInput(String tmpObjectKey) {
    validateTmpObjectKey(tmpObjectKey);
    validateExtension(extractExtension(tmpObjectKey));
  }

  private LocalDate exposedExerciseDate(VerificationRequest request) {
    return request.getVerificationKind() == VerificationKind.WORKOUT_RECORD
        ? request.getExerciseDate()
        : null;
  }

  private CompletedMethod resolveCompletedMethod(String sourceRef) {
    if (sourceRef == null || sourceRef.isBlank()) {
      return null;
    }
    return verificationTimePolicy.deriveCompletedMethod(sourceRef);
  }

  protected boolean isRetryableFailedToday(VerificationRequest existing) {
    return existing.getStatus() == VerificationStatus.FAILED
        && existing.getCreatedAt() != null
        && verificationTimePolicy.isToday(existing.getCreatedAt());
  }

  private void validateTmpObjectKey(String tmpObjectKey) {
    if (tmpObjectKey == null
        || tmpObjectKey.isBlank()
        || !tmpObjectKey.startsWith("private/workout/")) {
      throw new InvalidTmpObjectKeyException();
    }
  }

  private String extractExtension(String tmpObjectKey) {
    int dot = tmpObjectKey.lastIndexOf('.');
    if (dot <= 0 || dot == tmpObjectKey.length() - 1) {
      throw new InvalidVerificationImageExtensionException();
    }
    return tmpObjectKey.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private void validateExtension(String extension) {
    if ((extension.equals("heic") || extension.equals("heif"))
        && (!verificationImagePolicy.isHeifEnabled()
            || !imageCodecSupportPort.isHeifDecodeAvailable())) {
      throw new InvalidVerificationImageExtensionException();
    }
    if (!isAllowedExtension(extension)) {
      throw new InvalidVerificationImageExtensionException();
    }
  }

  private void validateUploadOwnership(Long userId, WorkoutUploadReference upload) {
    if (!userId.equals(upload.ownerUserId())) {
      throw new VerificationUploadForbiddenException();
    }
  }

  private void ensureObjectExists(String readObjectKey) {
    if (!objectStoragePort.exists(readObjectKey)) {
      throw new VerificationUploadNotFoundException();
    }
  }

  protected abstract boolean isAllowedExtension(String extension);

  protected abstract boolean needsExifValidation();

  protected abstract String sourceRefPrefix();

  protected abstract AiVerificationDecision analyzeWithAi(Path analysisImagePath);

  protected abstract LocalDate resolveVerifiedExerciseDate(AiVerificationDecision decision);

  protected abstract int analysisMaxLongEdge();

  protected abstract double analysisWebpQuality();
}
