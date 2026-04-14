package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.HandleTransferFailedOnchainCommand;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;

public interface CompensateTransferFailurePort {

  boolean supports(DomainReferenceType domainType);

  void compensate(HandleTransferFailedOnchainCommand command);
}
