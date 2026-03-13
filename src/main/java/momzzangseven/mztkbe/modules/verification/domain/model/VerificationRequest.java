package momzzangseven.mztkbe.modules.verification.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationRequest {

  private final Long id;
  private final String verificationId;
  private final Long userId;
  private final VerificationKind verificationKind;
  private final VerificationStatus status;
  private final LocalDate exerciseDate;
  private final LocalDateTime shotAtKst;
  private final String tmpObjectKey;
  private final RejectionReasonCode rejectionReasonCode;
  private final String rejectionReasonDetail;
  private final FailureCode failureCode;
  private final Instant createdAt;
  private final Instant updatedAt;

  public static VerificationRequest newPending(
      Long userId, VerificationKind kind, String tmpObjectKey) {
    return VerificationRequest.builder()
        .verificationId(UUID.randomUUID().toString())
        .userId(userId)
        .verificationKind(kind)
        .status(VerificationStatus.PENDING)
        .tmpObjectKey(tmpObjectKey)
        .build();
  }

  public VerificationRequest beginAnalysis() {
    if (status != VerificationStatus.PENDING && status != VerificationStatus.FAILED) {
      return this;
    }
    return toBuilder().status(VerificationStatus.ANALYZING).failureCode(null).build();
  }

  public VerificationRequest verify(LocalDate exerciseDate, LocalDateTime shotAtKst) {
    return toBuilder()
        .status(VerificationStatus.VERIFIED)
        .exerciseDate(exerciseDate)
        .shotAtKst(shotAtKst)
        .rejectionReasonCode(null)
        .rejectionReasonDetail(null)
        .failureCode(null)
        .build();
  }

  public VerificationRequest reject(
      RejectionReasonCode rejectionReasonCode,
      String rejectionReasonDetail,
      LocalDate exerciseDate,
      LocalDateTime shotAtKst) {
    return toBuilder()
        .status(VerificationStatus.REJECTED)
        .exerciseDate(exerciseDate)
        .shotAtKst(shotAtKst)
        .rejectionReasonCode(rejectionReasonCode)
        .rejectionReasonDetail(rejectionReasonDetail)
        .failureCode(null)
        .build();
  }

  public VerificationRequest rejectForMissingExif() {
    return reject(
        RejectionReasonCode.MISSING_EXIF_METADATA, "EXIF metadata is required", null, null);
  }

  public VerificationRequest rejectForExifDateMismatch(
      LocalDate exerciseDate, LocalDateTime shotAtKst) {
    return reject(
        RejectionReasonCode.EXIF_DATE_MISMATCH,
        "EXIF shot date must be today in KST",
        exerciseDate,
        shotAtKst);
  }

  public VerificationRequest fail(FailureCode failureCode) {
    return toBuilder().status(VerificationStatus.FAILED).failureCode(failureCode).build();
  }

  public VerificationRequest toAnalyzing() {
    return beginAnalysis();
  }

  public VerificationRequest toVerified(LocalDate exerciseDate, LocalDateTime shotAtKst) {
    return verify(exerciseDate, shotAtKst);
  }

  public VerificationRequest toRejected(
      RejectionReasonCode rejectionReasonCode,
      String rejectionReasonDetail,
      LocalDate exerciseDate,
      LocalDateTime shotAtKst) {
    return reject(rejectionReasonCode, rejectionReasonDetail, exerciseDate, shotAtKst);
  }

  public VerificationRequest toFailed(FailureCode failureCode) {
    return fail(failureCode);
  }
}
