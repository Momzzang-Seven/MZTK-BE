package momzzangseven.mztkbe.modules.verification.api.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.application.dto.LatestVerificationItem;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

@Builder
public record TodayWorkoutCompletionResponseDTO(
    boolean todayCompleted,
    CompletedMethod completedMethod,
    boolean rewardGrantedToday,
    int grantedXp,
    LocalDate earnedDate,
    LatestVerificationDTO latestVerification) {

  @Builder
  public record LatestVerificationDTO(
      String verificationId,
      VerificationKind verificationKind,
      VerificationStatus verificationStatus,
      VerificationRewardStatus rewardStatus,
      RejectionReasonCode rejectionReasonCode,
      FailureCode failureCode) {

    public static LatestVerificationDTO from(LatestVerificationItem item) {
      if (item == null) {
        return null;
      }
      return LatestVerificationDTO.builder()
          .verificationId(item.verificationId())
          .verificationKind(item.verificationKind())
          .verificationStatus(item.verificationStatus())
          .rewardStatus(item.rewardStatus())
          .rejectionReasonCode(item.rejectionReasonCode())
          .failureCode(item.failureCode())
          .build();
    }
  }

  public static TodayWorkoutCompletionResponseDTO from(TodayWorkoutCompletionResult result) {
    return TodayWorkoutCompletionResponseDTO.builder()
        .todayCompleted(result.todayCompleted())
        .completedMethod(result.completedMethod())
        .rewardGrantedToday(result.rewardGrantedToday())
        .grantedXp(result.grantedXp())
        .earnedDate(result.earnedDate())
        .latestVerification(LatestVerificationDTO.from(result.latestVerification()))
        .build();
  }
}
