package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CompensateTransferFailurePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewardFailureCompensator implements CompensateTransferFailurePort {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  public boolean supports(DomainReferenceType domainType) {
    return domainType == DomainReferenceType.QUESTION_REWARD;
  }

  @Override
  public void compensate(HandleTransferFailedOnchainCommand command) {
    Long postId = parseLongOrNull(command.referenceId());
    if (postId == null) {
      log.warn(
          "QUESTION_REWARD compensation skipped: txId={}, invalid referenceId={}",
          command.transactionId(),
          command.referenceId());
      return;
    }

    int updated =
        questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
            postId,
            QuestionRewardIntentStatus.FAILED_ONCHAIN,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED, QuestionRewardIntentStatus.SUBMITTED));

    if (updated > 0) {
      log.info(
          "QUESTION_REWARD compensation completed: txId={}, postId={}, failureReason={}",
          command.transactionId(),
          postId,
          command.failureReason());
      return;
    }

    log.info(
        "QUESTION_REWARD compensation no-op: txId={}, postId={} (intent not found or already finalized)",
        command.transactionId(),
        postId);
  }

  private Long parseLongOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
