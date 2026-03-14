package momzzangseven.mztkbe.modules.verification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationRewardProcessingResult;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationRewardService {

  private final VerificationRewardTransactionalService verificationRewardTransactionalService;

  public VerificationRewardProcessingResult process(
      Long userId, String verificationId, VerificationSubmissionPolicy policy) {
    try {
      return verificationRewardTransactionalService.processRewardAttempt(
          userId, verificationId, policy);
    } catch (RuntimeException ex) {
      log.warn("Verification reward failed: verificationId={}, userId={}", verificationId, userId, ex);
      return verificationRewardTransactionalService.markRewardFailed(verificationId);
    }
  }
}
