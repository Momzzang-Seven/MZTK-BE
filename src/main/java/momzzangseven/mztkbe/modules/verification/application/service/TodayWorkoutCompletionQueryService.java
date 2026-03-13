package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.LatestVerificationItem;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayWorkoutCompletionQueryService implements GetTodayWorkoutCompletionUseCase {

  private final XpLedgerQueryPort xpLedgerQueryPort;
  private final VerificationRequestPort verificationRequestPort;
  private final VerificationTimePolicy verificationTimePolicy;

  @Override
  public TodayWorkoutCompletionResult execute(Long userId) {
    LocalDate today = verificationTimePolicy.today();
    TodayRewardSnapshot reward = xpLedgerQueryPort.findTodayWorkoutReward(userId, today);
    Optional<VerificationRequest> latest =
        verificationRequestPort.findLatestUpdatedToday(userId, today);

    return TodayWorkoutCompletionResult.builder()
        .todayCompleted(reward.rewarded())
        .completedMethod(verificationTimePolicy.deriveCompletedMethod(reward.sourceRef()))
        .rewardGrantedToday(reward.rewarded())
        .grantedXp(reward.grantedXp())
        .earnedDate(reward.earnedDate())
        .latestVerification(
            latest
                .map(
                    it ->
                        LatestVerificationItem.builder()
                            .verificationId(it.getVerificationId())
                            .verificationKind(it.getVerificationKind())
                            .verificationStatus(it.getStatus())
                            .rejectionReasonCode(it.getRejectionReasonCode())
                            .failureCode(it.getFailureCode())
                            .build())
                .orElse(null))
        .build();
  }
}
