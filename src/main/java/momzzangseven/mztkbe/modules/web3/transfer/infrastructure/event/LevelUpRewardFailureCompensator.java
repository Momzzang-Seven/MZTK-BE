package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.LevelUpHistoryJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transfer.application.rollback.DomainTransferFailureCompensator;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import org.springframework.stereotype.Component;

/**
 * LEVEL_UP_REWARD has no separate mutable lock state; tx status itself is the source of truth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LevelUpRewardFailureCompensator implements DomainTransferFailureCompensator {

  private final LevelUpHistoryJpaRepository levelUpHistoryJpaRepository;

  @Override
  public boolean supports(DomainReferenceType domainType) {
    return domainType == DomainReferenceType.LEVEL_UP_REWARD;
  }

  @Override
  public void compensate(Web3TransactionFailedOnchainEvent event) {
    Long levelUpHistoryId = parseLongOrNull(event.referenceId());
    if (levelUpHistoryId == null) {
      log.warn(
          "LEVEL_UP_REWARD compensation skipped: txId={}, invalid referenceId={}",
          event.transactionId(),
          event.referenceId());
      return;
    }
    if (!levelUpHistoryJpaRepository.existsById(levelUpHistoryId)) {
      log.warn(
          "LEVEL_UP_REWARD compensation skipped: txId={}, levelUpHistoryId={} not found",
          event.transactionId(),
          levelUpHistoryId);
      return;
    }

    // No mutable state to roll back in level_up_histories after V010.
    log.info(
        "LEVEL_UP_REWARD compensation acknowledged: txId={}, levelUpHistoryId={}, failureReason={}",
        event.transactionId(),
        levelUpHistoryId,
        event.failureReason());
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
