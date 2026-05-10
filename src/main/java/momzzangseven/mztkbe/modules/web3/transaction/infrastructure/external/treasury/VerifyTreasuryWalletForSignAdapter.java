package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.treasury;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.VerifyTreasuryWalletForSignPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the transaction-side {@link VerifyTreasuryWalletForSignPort} by
 * delegating to the treasury module's {@link VerifyTreasuryWalletForSignUseCase}.
 *
 * <p>Bean name is namespaced ({@code transactionVerifyTreasuryWalletForSignAdapter}) to avoid a
 * collision with sibling bridging adapters introduced by the execution module in PR3.
 */
@Component("transactionVerifyTreasuryWalletForSignAdapter")
@RequiredArgsConstructor
public class VerifyTreasuryWalletForSignAdapter implements VerifyTreasuryWalletForSignPort {

  private final VerifyTreasuryWalletForSignUseCase verifyTreasuryWalletForSignUseCase;

  @Override
  public void verify(String walletAlias) {
    verifyTreasuryWalletForSignUseCase.execute(walletAlias);
  }
}
