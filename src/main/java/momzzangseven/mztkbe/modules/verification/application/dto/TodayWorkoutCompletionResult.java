package momzzangseven.mztkbe.modules.verification.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

/** Read model for restoring today's workout completion state on web refresh or re-entry. */
public record TodayWorkoutCompletionResult(
    boolean todayCompleted,
    String completedMethod,
    boolean rewardGrantedToday,
    int grantedXp,
    LocalDate earnedDate,
    LatestVerificationSummary latestVerification) {

  public record LatestVerificationSummary(
      String verificationId,
      VerificationKind verificationKind,
      VerificationStatus verificationStatus,
      RejectionReasonCode rejectionReasonCode,
      LocalDateTime decidedAt) {}
}
