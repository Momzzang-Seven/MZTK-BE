package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import lombok.Builder;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;

@Builder
public record TodayWorkoutCompletionResult(
    boolean todayCompleted,
    CompletedMethod completedMethod,
    boolean rewardGrantedToday,
    int grantedXp,
    LocalDate earnedDate,
    LatestVerificationItem latestVerification) {

  public static TodayWorkoutCompletionResult from(
      TodayRewardSnapshot reward,
      CompletedMethod completedMethod,
      LatestVerificationItem latestVerification) {
    return TodayWorkoutCompletionResult.builder()
        .todayCompleted(reward.rewarded())
        .completedMethod(completedMethod)
        .rewardGrantedToday(reward.rewarded())
        .grantedXp(reward.grantedXp())
        .earnedDate(reward.earnedDate())
        .latestVerification(latestVerification)
        .build();
  }
}
