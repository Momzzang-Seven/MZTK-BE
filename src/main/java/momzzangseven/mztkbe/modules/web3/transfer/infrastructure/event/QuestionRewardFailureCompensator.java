package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transfer.application.rollback.DomainTransferFailureCompensator;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewardFailureCompensator implements DomainTransferFailureCompensator {

  private final QuestionRewardIntentJpaRepository questionRewardIntentJpaRepository;

  @Override
  public boolean supports(DomainReferenceType domainType) {
    return domainType == DomainReferenceType.QUESTION_REWARD;
  }

  @Override
  public void compensate(Web3TransactionFailedOnchainEvent event) {
    Long postId = parseLongOrNull(event.referenceId());
    if (postId == null) {
      log.warn(
          "QUESTION_REWARD compensation skipped: txId={}, invalid referenceId={}",
          event.transactionId(),
          event.referenceId());
      return;
    }

    int updated =
        questionRewardIntentJpaRepository.updateStatusIfCurrentIn(
            postId,
            QuestionRewardIntentStatus.FAILED_ONCHAIN,
            EnumSet.of(
                QuestionRewardIntentStatus.PREPARE_REQUIRED,
                QuestionRewardIntentStatus.SUBMITTED));

    if (updated > 0) {
      log.info(
          "QUESTION_REWARD compensation completed: txId={}, postId={}, failureReason={}",
          event.transactionId(),
          postId,
          event.failureReason());
      return;
    }

    log.info(
        "QUESTION_REWARD compensation no-op: txId={}, postId={} (intent not found or already finalized)",
        event.transactionId(),
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
