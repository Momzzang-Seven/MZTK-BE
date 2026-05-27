package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceSlotTransitionCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceSlotView;

public interface RecordSponsorNonceSlotTransitionPort {

  SponsorNonceSlotView recordTransition(RecordSponsorNonceSlotTransitionCommand command);
}
