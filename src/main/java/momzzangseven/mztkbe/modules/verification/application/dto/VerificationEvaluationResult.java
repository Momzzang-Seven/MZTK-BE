package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;

public record VerificationEvaluationResult(
    FailureCode failureCode,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    LocalDate exerciseDate,
    LocalDateTime shotAtKst) {

  public static VerificationEvaluationResult verified(
      LocalDate exerciseDate, LocalDateTime shotAtKst) {
    return new VerificationEvaluationResult(null, null, null, exerciseDate, shotAtKst);
  }

  public static VerificationEvaluationResult rejected(
      RejectionReasonCode rejectionReasonCode,
      String rejectionReasonDetail,
      LocalDate exerciseDate,
      LocalDateTime shotAtKst) {
    return new VerificationEvaluationResult(
        null, rejectionReasonCode, rejectionReasonDetail, exerciseDate, shotAtKst);
  }

  public static VerificationEvaluationResult failed(FailureCode failureCode) {
    return new VerificationEvaluationResult(failureCode, null, null, null, null);
  }

  public boolean verified() {
    return failureCode == null && rejectionReasonCode == null;
  }

  public boolean rejected() {
    return rejectionReasonCode != null;
  }

  public boolean failed() {
    return failureCode != null;
  }
}
