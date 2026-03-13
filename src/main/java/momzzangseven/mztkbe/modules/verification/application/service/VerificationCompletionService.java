package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.RejectionReasonCode;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationCompletionService {

  private final VerificationRequestPort verificationRequestPort;
  private final GrantXpPort grantXpPort;
  private final XpLedgerQueryPort xpLedgerQueryPort;
  private final VerificationTimePolicy verificationTimePolicy;
  private final VerificationSubmissionResultFactory verificationSubmissionResultFactory;

  public SubmitWorkoutVerificationResult existingResult(
      Long userId, TodayRewardSnapshot todayReward, VerificationRequest request) {
    return verificationSubmissionResultFactory.from(
        request, 0, resolveCompletedMethodSourceRef(userId, todayReward, request));
  }

  @Transactional
  public SubmitWorkoutVerificationResult complete(
      Long userId,
      TodayRewardSnapshot todayReward,
      String verificationId,
      VerificationEvaluationResult evaluation,
      VerificationSubmissionPolicy policy) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () -> new IllegalStateException("verification row must exist before completion"));

    if (locked.getStatus() == VerificationStatus.VERIFIED
        || locked.getStatus() == VerificationStatus.REJECTED) {
      return existingResult(userId, todayReward, locked);
    }

    if (evaluation.failed()) {
      VerificationRequest failed =
          verificationRequestPort.save(locked.fail(evaluation.failureCode()));
      return verificationSubmissionResultFactory.from(failed, 0, null);
    }

    if (evaluation.rejected()) {
      VerificationRequest rejected =
          verificationRequestPort.save(applyRejection(locked, evaluation));
      return verificationSubmissionResultFactory.from(rejected, 0, null);
    }

    VerificationRequest verified =
        verificationRequestPort.save(
            locked.verify(evaluation.exerciseDate(), evaluation.shotAtKst()));
    String sourceRef = policy.sourceRefPrefix() + verified.getVerificationId();
    int grantedXp =
        grantXpPort.grantWorkoutXp(
            userId, verified.getVerificationKind(), verified.getVerificationId(), sourceRef);
    String completedMethodSourceRef =
        grantedXp == 0
            ? xpLedgerQueryPort
                .findTodayWorkoutReward(userId, verificationTimePolicy.today())
                .sourceRef()
            : sourceRef;
    return verificationSubmissionResultFactory.from(verified, grantedXp, completedMethodSourceRef);
  }

  private VerificationRequest applyRejection(
      VerificationRequest request, VerificationEvaluationResult evaluation) {
    if (evaluation.rejectionReasonCode() == RejectionReasonCode.MISSING_EXIF_METADATA) {
      return request.rejectForMissingExif();
    }
    if (evaluation.rejectionReasonCode() == RejectionReasonCode.EXIF_DATE_MISMATCH) {
      return request.rejectForExifDateMismatch(evaluation.exerciseDate(), evaluation.shotAtKst());
    }
    return request.reject(
        evaluation.rejectionReasonCode(),
        evaluation.rejectionReasonDetail(),
        evaluation.exerciseDate(),
        evaluation.shotAtKst());
  }

  private String resolveCompletedMethodSourceRef(
      Long userId, TodayRewardSnapshot todayReward, VerificationRequest request) {
    if (request.getStatus() != VerificationStatus.VERIFIED) {
      return null;
    }
    if (todayReward.rewarded()) {
      return todayReward.sourceRef();
    }
    return xpLedgerQueryPort
        .findTodayWorkoutReward(userId, verificationTimePolicy.today())
        .sourceRef();
  }
}
