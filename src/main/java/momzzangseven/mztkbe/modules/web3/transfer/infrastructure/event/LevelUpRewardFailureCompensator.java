package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CheckLevelUpHistoryExistsPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.CompensateTransferFailurePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import org.springframework.stereotype.Component;

/** LEVEL_UP_REWARD has no separate mutable lock state; tx status itself is the source of truth. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LevelUpRewardFailureCompensator implements CompensateTransferFailurePort {

  private final CheckLevelUpHistoryExistsPort checkLevelUpHistoryExistsPort;

  @Override
  public boolean supports(DomainReferenceType domainType) {
    return domainType == DomainReferenceType.LEVEL_UP_REWARD;
  }

  @Override
  public void compensate(HandleTransferFailedOnchainCommand command) {
    Long levelUpHistoryId = parseLongOrNull(command.referenceId());
    if (levelUpHistoryId == null) {
      log.warn(
          "LEVEL_UP_REWARD compensation skipped: txId={}, invalid referenceId={}",
          command.transactionId(),
          command.referenceId());
      return;
    }
    if (!checkLevelUpHistoryExistsPort.existsById(levelUpHistoryId)) {
      log.warn(
          "LEVEL_UP_REWARD compensation skipped: txId={}, levelUpHistoryId={} not found",
          command.transactionId(),
          levelUpHistoryId);
      return;
    }

    // No mutable state to roll back in level_up_histories after V010.
    log.info(
        "LEVEL_UP_REWARD compensation acknowledged: txId={}, levelUpHistoryId={}, failureReason={}",
        command.transactionId(),
        levelUpHistoryId,
        command.failureReason());
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
