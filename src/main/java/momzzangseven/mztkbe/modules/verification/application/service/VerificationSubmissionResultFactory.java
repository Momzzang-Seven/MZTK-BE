package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationSubmissionResultFactory {

  private final VerificationTimePolicy verificationTimePolicy;

  public SubmitWorkoutVerificationResult from(
      VerificationRequest request, int grantedXp, String sourceRef) {
    return SubmitWorkoutVerificationResult.builder()
        .verificationId(request.getVerificationId())
        .verificationKind(request.getVerificationKind())
        .verificationStatus(request.getStatus())
        .rewardStatus(request.getRewardStatus())
        .exerciseDate(exposedExerciseDate(request))
        .completionStatus(
            request.getStatus() == VerificationStatus.VERIFIED
                    && request.getRewardStatus() == VerificationRewardStatus.SUCCEEDED
                ? CompletionStatus.COMPLETED
                : CompletionStatus.NOT_COMPLETED)
        .grantedXp(grantedXp)
        .completedMethod(resolveCompletedMethod(sourceRef))
        .rejectionReasonCode(request.getRejectionReasonCode())
        .rejectionReasonDetail(request.getRejectionReasonDetail())
        .failureCode(request.getFailureCode())
        .build();
  }

  private LocalDate exposedExerciseDate(VerificationRequest request) {
    return request.getVerificationKind() == VerificationKind.WORKOUT_RECORD
        ? request.getExerciseDate()
        : null;
  }

  private CompletedMethod resolveCompletedMethod(String sourceRef) {
    if (sourceRef == null || sourceRef.isBlank()) {
      return null;
    }
    return verificationTimePolicy.deriveCompletedMethod(sourceRef);
  }
}
