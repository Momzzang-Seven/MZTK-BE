package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.VerifyMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.VerifyTreasuryWalletForSignUseCase;
import org.springframework.stereotype.Component;

/** Treasury bridge for the marketplace admin signer pre-sign gate. */
@Component
@RequiredArgsConstructor
public class VerifyMarketplaceAdminSignerWalletAdapter
    implements VerifyMarketplaceAdminSignerWalletPort {

  private final VerifyTreasuryWalletForSignUseCase verifyTreasuryWalletForSignUseCase;

  @Override
  public void verify(String walletAlias) {
    verifyTreasuryWalletForSignUseCase.execute(walletAlias);
  }
}
