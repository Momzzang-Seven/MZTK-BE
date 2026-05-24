package momzzangseven.mztkbe.modules.web3.transaction.application.port.in.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceCoordinationResult;

public interface CoordinateSponsorNonceUseCase {

  SponsorNonceCoordinationResult execute(SponsorNonceCoordinationCommand command);
}
