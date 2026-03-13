package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.verification.VerificationAlreadyCompletedTodayException;
import momzzangseven.mztkbe.global.error.verification.VerificationKindMismatchException;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayRewardSnapshot;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationEvaluationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationReservation;
import momzzangseven.mztkbe.modules.verification.application.port.out.VerificationRequestPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.XpLedgerQueryPort;
import momzzangseven.mztkbe.modules.verification.domain.model.VerificationRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationSubmissionOrchestrator {

  private final VerificationRequestPort verificationRequestPort;
  private final XpLedgerQueryPort xpLedgerQueryPort;
  private final VerificationTimePolicy verificationTimePolicy;
  private final VerificationSubmissionValidator verificationSubmissionValidator;
  private final VerificationSubmissionAccessService verificationSubmissionAccessService;
  private final VerificationAnalysisService verificationAnalysisService;
  private final VerificationCompletionService verificationCompletionService;

  public SubmitWorkoutVerificationResult submit(
      SubmitWorkoutVerificationCommand command, VerificationSubmissionPolicy policy) {
    if (command.kind() != policy.kind()) {
      throw new IllegalArgumentException("Submit command kind does not match policy");
    }

    TodayRewardSnapshot todayReward = loadTodayRewardOrThrow(command.userId());
    VerificationRequest existing =
        verificationRequestPort.findByTmpObjectKey(command.tmpObjectKey()).orElse(null);
    if (existing != null) {
      return handleExisting(command, policy, todayReward, existing);
    }
    return submitNew(command, policy, todayReward);
  }

  private SubmitWorkoutVerificationResult submitNew(
      SubmitWorkoutVerificationCommand command,
      VerificationSubmissionPolicy policy,
      TodayRewardSnapshot todayReward) {
    verificationSubmissionValidator.validateSubmitInput(command.tmpObjectKey(), policy);

    VerificationReservation reservation = verificationSubmissionAccessService.reserveNew(command);
    if (!reservation.readyForAnalysis()) {
      return handleExisting(command, policy, todayReward, reservation.request());
    }

    VerificationEvaluationResult evaluation =
        verificationAnalysisService.evaluate(command, reservation.upload(), policy);
    return verificationCompletionService.complete(
        command.userId(),
        todayReward,
        reservation.request().getVerificationId(),
        evaluation,
        policy);
  }

  private SubmitWorkoutVerificationResult handleExisting(
      SubmitWorkoutVerificationCommand command,
      VerificationSubmissionPolicy policy,
      TodayRewardSnapshot todayReward,
      VerificationRequest existing) {
    verificationSubmissionValidator.validateExistingOwnership(command.userId(), existing);
    if (existing.getVerificationKind() != command.kind()) {
      throw new VerificationKindMismatchException();
    }
    if (!verificationSubmissionAccessService.isRetryableFailedToday(existing)) {
      return verificationCompletionService.existingResult(command.userId(), todayReward, existing);
    }

    verificationSubmissionValidator.validateSubmitInput(command.tmpObjectKey(), policy);
    VerificationReservation reservation =
        verificationSubmissionAccessService.reserveRetry(command, existing);
    if (!reservation.readyForAnalysis()) {
      TodayRewardSnapshot refreshedTodayReward =
          xpLedgerQueryPort.findTodayWorkoutReward(
              command.userId(), verificationTimePolicy.today());
      return verificationCompletionService.existingResult(
          command.userId(), refreshedTodayReward, reservation.request());
    }

    VerificationEvaluationResult evaluation =
        verificationAnalysisService.evaluate(command, reservation.upload(), policy);
    return verificationCompletionService.complete(
        command.userId(),
        todayReward,
        reservation.request().getVerificationId(),
        evaluation,
        policy);
  }

  private TodayRewardSnapshot loadTodayRewardOrThrow(Long userId) {
    LocalDate today = verificationTimePolicy.today();
    TodayRewardSnapshot todayReward = xpLedgerQueryPort.findTodayWorkoutReward(userId, today);
    if (todayReward.rewarded()) {
      throw new VerificationAlreadyCompletedTodayException(
          verificationTimePolicy.deriveCompletedMethod(todayReward.sourceRef()),
          todayReward.earnedDate());
    }
    return todayReward;
  }
}
