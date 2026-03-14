package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationRewardProcessingResult;
import momzzangseven.mztkbe.modules.verification.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationRewardTransactionalService {

  private final VerificationRequestPort verificationRequestPort;
  private final GrantXpPort grantXpPort;
  private final XpLedgerQueryPort xpLedgerQueryPort;
  private final VerificationTimePolicy verificationTimePolicy;

  @Transactional
  public VerificationRewardProcessingResult processRewardAttempt(
      Long userId, String verificationId, VerificationSubmissionPolicy policy) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () -> new IllegalStateException("verification row must exist before reward"));

    if (locked.getStatus() != VerificationStatus.VERIFIED) {
      return new VerificationRewardProcessingResult(locked, 0, locked.getRewardSourceRef());
    }

    if (!locked.isRewardRetryable()) {
      return new VerificationRewardProcessingResult(locked, 0, locked.getRewardSourceRef());
    }

    String requestedSourceRef = policy.sourceRefPrefix() + locked.getVerificationId();
    int grantedXp =
        grantXpPort.grantWorkoutXp(
            userId, locked.getVerificationKind(), locked.getVerificationId(), requestedSourceRef);

    String resolvedSourceRef =
        grantedXp == 0 ? resolveTodayRewardSourceRef(userId) : requestedSourceRef;

    if (resolvedSourceRef == null || resolvedSourceRef.isBlank()) {
      VerificationRequest failed = verificationRequestPort.save(locked.rewardFailed());
      return new VerificationRewardProcessingResult(failed, 0, null);
    }

    VerificationRequest succeeded =
        verificationRequestPort.save(locked.rewardSucceeded(resolvedSourceRef));
    return new VerificationRewardProcessingResult(succeeded, grantedXp, resolvedSourceRef);
  }

  @Transactional
  public VerificationRewardProcessingResult markRewardFailed(String verificationId) {
    VerificationRequest locked =
        verificationRequestPort
            .findByVerificationIdForUpdate(verificationId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "verification row must exist before reward failure handling"));

    if (!locked.isRewardRetryable()) {
      return new VerificationRewardProcessingResult(locked, 0, locked.getRewardSourceRef());
    }

    VerificationRequest failed = verificationRequestPort.save(locked.rewardFailed());
    return new VerificationRewardProcessingResult(failed, 0, null);
  }

  private String resolveTodayRewardSourceRef(Long userId) {
    TodayRewardSnapshot snapshot =
        xpLedgerQueryPort.findTodayWorkoutReward(userId, verificationTimePolicy.today());
    return snapshot.rewarded() ? snapshot.sourceRef() : null;
  }
}
