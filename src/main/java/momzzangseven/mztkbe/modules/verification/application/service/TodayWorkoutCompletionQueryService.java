package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.LatestVerificationItem;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
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
    LatestVerificationItem latestVerification =
        verificationRequestPort
            .findLatestUpdatedToday(userId, today)
            .map(LatestVerificationItem::from)
            .orElse(null);
    return TodayWorkoutCompletionResult.from(
        reward,
        verificationTimePolicy.deriveCompletedMethod(reward.sourceRef()),
        latestVerification);
  }
}
