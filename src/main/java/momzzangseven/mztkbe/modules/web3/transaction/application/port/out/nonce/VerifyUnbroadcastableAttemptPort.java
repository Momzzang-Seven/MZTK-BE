package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce.VerifyUnbroadcastableAttemptCommand;

public interface VerifyUnbroadcastableAttemptPort {

  boolean verifyUnbroadcastable(VerifyUnbroadcastableAttemptCommand command);
}
