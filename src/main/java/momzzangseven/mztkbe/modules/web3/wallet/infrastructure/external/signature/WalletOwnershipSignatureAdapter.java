package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.signature;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.signature.application.dto.VerifySignatureCommand;
import momzzangseven.mztkbe.modules.web3.signature.application.port.in.VerifySignatureUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.VerifyWalletOwnershipSignaturePort;
import org.springframework.stereotype.Component;

/** Adapter between wallet registration and signature verification use cases. */
@Component
@RequiredArgsConstructor
public class WalletOwnershipSignatureAdapter implements VerifyWalletOwnershipSignaturePort {

  private final VerifySignatureUseCase verifySignatureUseCase;

  @Override
  public boolean verify(
      String challengeMessage, String nonce, String signature, String expectedAddress) {
    return verifySignatureUseCase.execute(
        new VerifySignatureCommand(challengeMessage, nonce, signature, expectedAddress));
  }
}
