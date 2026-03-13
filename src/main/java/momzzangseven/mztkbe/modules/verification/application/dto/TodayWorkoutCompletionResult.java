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
    LatestVerificationItem latestVerification) {}
