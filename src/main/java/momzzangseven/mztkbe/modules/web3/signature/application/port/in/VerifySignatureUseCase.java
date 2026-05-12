package momzzangseven.mztkbe.modules.web3.signature.application.port.in;

import momzzangseven.mztkbe.modules.web3.signature.application.dto.VerifySignatureCommand;

public interface VerifySignatureUseCase {

  boolean execute(VerifySignatureCommand command);
}
