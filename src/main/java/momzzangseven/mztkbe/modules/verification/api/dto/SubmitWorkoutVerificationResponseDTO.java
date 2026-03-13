package momzzangseven.mztkbe.modules.verification.api.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record SubmitWorkoutVerificationResponseDTO(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    LocalDate exerciseDate,
    CompletionStatus completionStatus,
    int grantedXp,
    CompletedMethod completedMethod,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    FailureCode failureCode) {

  public static SubmitWorkoutVerificationResponseDTO from(SubmitWorkoutVerificationResult result) {
    return SubmitWorkoutVerificationResponseDTO.builder()
        .verificationId(result.verificationId())
        .verificationKind(result.verificationKind())
        .verificationStatus(result.verificationStatus())
        .exerciseDate(result.exerciseDate())
        .completionStatus(result.completionStatus())
        .grantedXp(result.grantedXp())
        .completedMethod(result.completedMethod())
        .rejectionReasonCode(result.rejectionReasonCode())
        .rejectionReasonDetail(result.rejectionReasonDetail())
        .failureCode(result.failureCode())
        .build();
  }
}
