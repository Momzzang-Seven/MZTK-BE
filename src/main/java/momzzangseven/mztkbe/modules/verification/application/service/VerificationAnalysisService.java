package momzzangseven.mztkbe.modules.verification.application.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.WorkoutUploadReference;
import momzzangseven.mztkbe.modules.verification.application.exception.AiMalformedResponseException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiResponseSchemaInvalidException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiTimeoutException;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationAnalysisService {

  private final VerificationSourceImageService verificationSourceImageService;
  private final PrepareAnalysisImagePort prepareAnalysisImagePort;
  private final WorkoutImageAiPort workoutImageAiPort;
  private final VerificationSubmissionValidator verificationSubmissionValidator;
  private final VerificationTimePolicy verificationTimePolicy;

  public VerificationEvaluationResult evaluate(
      SubmitWorkoutVerificationCommand command,
      WorkoutUploadReference upload,
      VerificationSubmissionPolicy policy) {
    String extension = verificationSubmissionValidator.extractExtension(command.tmpObjectKey());
    Optional<ExifMetadataInfo> exif = Optional.empty();

    try {
      if (policy.requiresExifValidation()) {
        exif = verificationSourceImageService.extractExif(upload.readObjectKey(), extension);
        VerificationEvaluationResult exifRejection = validateExif(exif);
        if (exifRejection != null) {
          return exifRejection;
        }
      }

      try (PreparedOriginalImage originalImage =
          verificationSourceImageService.prepareOriginalImage(upload.readObjectKey(), extension)) {
        return analyzePreparedImage(originalImage.path(), policy, exif);
      } catch (IOException ex) {
        return VerificationEvaluationResult.failed(FailureCode.ORIGINAL_IMAGE_READ_FAILED);
      } catch (VerificationAnalysisFailure ex) {
        return VerificationEvaluationResult.failed(ex.failureCode());
      }
    } catch (IOException ex) {
      return VerificationEvaluationResult.failed(FailureCode.ORIGINAL_IMAGE_READ_FAILED);
    }
  }

  private VerificationEvaluationResult validateExif(Optional<ExifMetadataInfo> exif) {
    if (exif.isEmpty()) {
      return VerificationEvaluationResult.rejected(
          RejectionReasonCode.MISSING_EXIF_METADATA, "EXIF metadata is required", null, null);
    }
    if (!verificationTimePolicy.isToday(exif.get().shotAtKst().toLocalDate())) {
      return VerificationEvaluationResult.rejected(
          RejectionReasonCode.EXIF_DATE_MISMATCH,
          "EXIF shot date must be today in KST",
          exif.get().shotAtKst().toLocalDate(),
          exif.get().shotAtKst());
    }
    return null;
  }

  private VerificationEvaluationResult analyzePreparedImage(
      Path originalPath, VerificationSubmissionPolicy policy, Optional<ExifMetadataInfo> exif) {
    try (PreparedAnalysisImage analysisImage =
        prepareAnalysisImagePort.prepare(
            originalPath, policy.analysisMaxLongEdge(), policy.analysisWebpQuality())) {
      AiVerificationDecision decision = analyzeWithAi(policy, analysisImage.path());
      if (!decision.approved()) {
        if (decision.rejectionReasonCode() == null) {
          return VerificationEvaluationResult.failed(FailureCode.AI_RESPONSE_SCHEMA_INVALID);
        }
        return VerificationEvaluationResult.rejected(
            decision.rejectionReasonCode(),
            decision.rejectionReasonDetail(),
            decision.exerciseDate(),
            exif.map(ExifMetadataInfo::shotAtKst).orElse(null));
      }
      return VerificationEvaluationResult.verified(
          policy.resolveVerifiedExerciseDate(decision),
          exif.map(ExifMetadataInfo::shotAtKst).orElse(null));
    } catch (IOException ex) {
      return VerificationEvaluationResult.failed(FailureCode.ANALYSIS_IMAGE_GENERATION_FAILED);
    }
  }

  private AiVerificationDecision analyzeWithAi(
      VerificationSubmissionPolicy policy, Path analysisImagePath) {
    try {
      return policy.analyze(workoutImageAiPort, analysisImagePath);
    } catch (AiTimeoutException ex) {
      throw new VerificationAnalysisFailure(FailureCode.EXTERNAL_AI_TIMEOUT, ex);
    } catch (AiMalformedResponseException ex) {
      throw new VerificationAnalysisFailure(FailureCode.EXTERNAL_AI_MALFORMED_RESPONSE, ex);
    } catch (AiResponseSchemaInvalidException ex) {
      throw new VerificationAnalysisFailure(FailureCode.AI_RESPONSE_SCHEMA_INVALID, ex);
    } catch (AiUnavailableException ex) {
      throw new VerificationAnalysisFailure(FailureCode.EXTERNAL_AI_UNAVAILABLE, ex);
    }
  }

  private static final class VerificationAnalysisFailure extends RuntimeException {

    private final FailureCode failureCode;

    private VerificationAnalysisFailure(FailureCode failureCode, Throwable cause) {
      super(cause);
      this.failureCode = failureCode;
    }

    private FailureCode failureCode() {
      return failureCode;
    }
  }
}
