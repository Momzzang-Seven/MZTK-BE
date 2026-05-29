package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.RecordSponsorNonceEvidenceCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.SponsorNonceEvidenceView;

public interface RecordSponsorNonceEvidencePort {

  SponsorNonceEvidenceView record(RecordSponsorNonceEvidenceCommand command);
}
