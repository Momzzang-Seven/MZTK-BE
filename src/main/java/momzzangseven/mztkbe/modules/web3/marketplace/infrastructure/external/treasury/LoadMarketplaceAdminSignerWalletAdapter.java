package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminSignerWalletView;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceAdminSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

/** Treasury bridge that resolves the MARKETPLACE_SIGNER wallet for marketplace admin execution. */
@Component
@RequiredArgsConstructor
public class LoadMarketplaceAdminSignerWalletAdapter
    implements LoadMarketplaceAdminSignerWalletPort {

  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Override
  public Optional<MarketplaceAdminSignerWalletView> load() {
    return loadTreasuryWalletByRoleUseCase
        .execute(TreasuryRole.MARKETPLACE_SIGNER)
        .map(LoadMarketplaceAdminSignerWalletAdapter::toView);
  }

  private static MarketplaceAdminSignerWalletView toView(TreasuryWalletView source) {
    return new MarketplaceAdminSignerWalletView(
        source.walletAlias(),
        source.kmsKeyId(),
        source.walletAddress(),
        source.status() == TreasuryWalletStatus.ACTIVE);
  }
}
