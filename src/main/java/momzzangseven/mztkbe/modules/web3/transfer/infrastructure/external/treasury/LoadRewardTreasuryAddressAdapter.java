package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadRewardTreasuryAddressPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the transfer-side {@link LoadRewardTreasuryAddressPort} by
 * delegating to the treasury module's {@link LoadTreasuryWalletByRoleUseCase} with the canonical
 * {@code REWARD} role.
 *
 * <p>The {@link TreasuryRole} import is intentionally confined to this bridging adapter — that is
 * what keeps transfer-layer callers (level reward path) free of {@code treasury.domain}
 * dependencies. The bean name is namespaced to avoid collisions with sibling sidecars in other
 * modules that delegate to the same treasury input port.
 */
@Component("transferLoadRewardTreasuryAddressAdapter")
@RequiredArgsConstructor
public class LoadRewardTreasuryAddressAdapter implements LoadRewardTreasuryAddressPort {

  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Override
  public Optional<String> loadAddress() {
    return loadTreasuryWalletByRoleUseCase
        .execute(TreasuryRole.REWARD)
        .map(TreasuryWalletView::walletAddress);
  }
}
