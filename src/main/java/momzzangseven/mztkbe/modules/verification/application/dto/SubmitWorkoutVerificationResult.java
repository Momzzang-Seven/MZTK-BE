package momzzangseven.mztkbe.modules.verification.application.dto;

import momzzangseven.mztkbe.modules.verification.domain.vo.FailureCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;

/**
 * Terminal submit response expected by the web client.
 *
 * <p>The submit path is synchronous: verification, completion gating, and reward decision are all
 * resolved before the response is returned.
 */
public record SubmitWorkoutVerificationResult(
    String verificationId,
    VerificationKind verificationKind,
    VerificationStatus verificationStatus,
    CompletionStatus completionStatus,
    RewardStatus rewardStatus,
    int grantedXp,
    String completedMethod,
    RejectionReasonCode rejectionReasonCode,
    String rejectionReasonDetail,
    FailureCode failureCode) {

  public enum CompletionStatus {
    COMPLETED,
    ALREADY_COMPLETED_TODAY,
    NOT_COMPLETED
  }

  public enum RewardStatus {
    GRANTED,
    NOT_GRANTED,
    NOT_GRANTED_ALREADY_COMPLETED_TODAY,
    FAILED
  }
}
