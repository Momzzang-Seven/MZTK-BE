package momzzangseven.mztkbe.modules.web3.signature.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.signature.application.dto.VerifySignatureCommand;
import momzzangseven.mztkbe.modules.web3.signature.application.port.in.VerifySignatureUseCase;
import momzzangseven.mztkbe.modules.web3.signature.application.port.out.VerifySignaturePort;
import org.springframework.stereotype.Service;

/** Signature verification use case facade. */
@Service
@RequiredArgsConstructor
public class VerifySignatureService implements VerifySignatureUseCase {

  private final VerifySignaturePort verifySignaturePort;

  @Override
  public boolean execute(VerifySignatureCommand command) {
    return verifySignaturePort.verify(
        command.challengeMessage(),
        command.nonce(),
        command.signature(),
        command.expectedAddress());
  }
}
