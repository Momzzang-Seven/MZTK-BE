package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationRewardProcessingResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationRewardStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationCompletionService {

  private final VerificationStateTransitionService verificationStateTransitionService;
  private final VerificationRewardService verificationRewardService;
  private final XpLedgerQueryPort xpLedgerQueryPort;
  private final VerificationTimePolicy verificationTimePolicy;
  private final VerificationSubmissionResultFactory verificationSubmissionResultFactory;

  public SubmitWorkoutVerificationResult existingResult(
      Long userId, TodayRewardSnapshot todayReward, VerificationRequest request) {
    return verificationSubmissionResultFactory.from(
        request, 0, resolveCompletedMethodSourceRef(userId, todayReward, request));
  }

  public SubmitWorkoutVerificationResult complete(
      Long userId,
      TodayRewardSnapshot todayReward,
      String verificationId,
      VerificationEvaluationResult evaluation,
      VerificationSubmissionPolicy policy) {
    VerificationRequest completed =
        verificationStateTransitionService.applyEvaluation(verificationId, evaluation);

    if (completed.getStatus() != VerificationStatus.VERIFIED) {
      return verificationSubmissionResultFactory.from(completed, 0, null);
    }

    if (completed.getRewardStatus() == VerificationRewardStatus.SUCCEEDED) {
      return existingResult(userId, todayReward, completed);
    }

    VerificationRewardProcessingResult rewardResult =
        verificationRewardService.process(userId, verificationId, policy);
    return verificationSubmissionResultFactory.from(
        rewardResult.request(), rewardResult.grantedXp(), rewardResult.completedMethodSourceRef());
  }

  public SubmitWorkoutVerificationResult retryReward(
      Long userId,
      TodayRewardSnapshot todayReward,
      String verificationId,
      VerificationSubmissionPolicy policy) {
    VerificationRewardProcessingResult rewardResult =
        verificationRewardService.process(userId, verificationId, policy);
    String completedMethodSourceRef =
        rewardResult.completedMethodSourceRef() != null
            ? rewardResult.completedMethodSourceRef()
            : resolveCompletedMethodSourceRef(userId, todayReward, rewardResult.request());
    return verificationSubmissionResultFactory.from(
        rewardResult.request(), rewardResult.grantedXp(), completedMethodSourceRef);
  }

  private String resolveCompletedMethodSourceRef(
      Long userId, TodayRewardSnapshot todayReward, VerificationRequest request) {
    if (request.getStatus() != VerificationStatus.VERIFIED
        || request.getRewardStatus() != VerificationRewardStatus.SUCCEEDED) {
      return null;
    }
    if (todayReward.rewarded()) {
      return todayReward.sourceRef();
    }
    if (request.getRewardSourceRef() != null && !request.getRewardSourceRef().isBlank()) {
      return request.getRewardSourceRef();
    }
    return xpLedgerQueryPort
        .findTodayWorkoutReward(userId, verificationTimePolicy.today())
        .sourceRef();
  }
}
