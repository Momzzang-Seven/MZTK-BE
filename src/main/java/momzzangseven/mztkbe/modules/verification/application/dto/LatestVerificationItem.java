package momzzangseven.mztkbe.modules.verification.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record LatestVerificationItem(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    VerificationRewardStatus rewardStatus,
    RejectionReasonCode rejectionReasonCode,
    FailureCode failureCode) {

  public static LatestVerificationItem from(VerificationRequest request) {
    return LatestVerificationItem.builder()
        .verificationId(request.getVerificationId())
        .verificationKind(request.getVerificationKind())
        .verificationStatus(request.getStatus())
        .rewardStatus(request.getRewardStatus())
        .rejectionReasonCode(request.getRejectionReasonCode())
        .failureCode(request.getFailureCode())
        .build();
  }
}
