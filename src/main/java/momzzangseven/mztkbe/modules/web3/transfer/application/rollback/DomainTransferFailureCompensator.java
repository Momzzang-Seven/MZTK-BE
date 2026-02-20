package momzzangseven.mztkbe.modules.web3.transfer.application.rollback;

import momzzangseven.mztkbe.modules.web3.transaction.domain.event.Web3TransactionFailedOnchainEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;

/** Domain-specific compensator for FAILED_ONCHAIN cases. */
public interface DomainTransferFailureCompensator {
  boolean supports(DomainReferenceType domainType);

  void compensate(Web3TransactionFailedOnchainEvent event);
}
