package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.application.rollback.DomainTransferFailureCompensator;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferIdempotencyKeyFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class Web3TransferFailedOnchainEventHandler {

  private final List<DomainTransferFailureCompensator> compensators;

  @EventListener
  @Transactional
  public void handle(Web3TransactionFailedOnchainEvent event) {
    DomainReferenceType domainType = resolveDomainType(event);

    if (domainType == null) {
      return;
    }

    DomainTransferFailureCompensator compensator =
        compensators.stream().filter(it -> it.supports(domainType)).findFirst().orElse(null);

    if (compensator == null) {
      log.warn(
          "FAILED_ONCHAIN compensation skipped: txId={}, domainType={}, referenceId={} (no compensator)",
          event.transactionId(),
          domainType,
          event.referenceId());
      return;
    }

    compensator.compensate(event);
  }

  private DomainReferenceType resolveDomainType(Web3TransactionFailedOnchainEvent event) {
    DomainReferenceType parsedFromIdempotency =
        TokenTransferIdempotencyKeyFactory.parseDomainType(event.idempotencyKey());
    if (parsedFromIdempotency != null) {
      return parsedFromIdempotency;
    }
    if (event.referenceType() == Web3ReferenceType.LEVEL_UP_REWARD) {
      return DomainReferenceType.LEVEL_UP_REWARD;
    }
    return null;
  }
}
