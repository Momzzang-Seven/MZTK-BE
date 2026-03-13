package momzzangseven.mztkbe.modules.verification.api.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record VerificationDetailResponseDTO(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    LocalDate exerciseDate,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    FailureCode failureCode) {

  public static VerificationDetailResponseDTO from(VerificationDetailResult result) {
    return VerificationDetailResponseDTO.builder()
        .verificationId(result.verificationId())
        .verificationKind(result.verificationKind())
        .verificationStatus(result.verificationStatus())
        .exerciseDate(result.exerciseDate())
        .rejectionReasonCode(result.rejectionReasonCode())
        .rejectionReasonDetail(result.rejectionReasonDetail())
        .failureCode(result.failureCode())
        .build();
  }
}
