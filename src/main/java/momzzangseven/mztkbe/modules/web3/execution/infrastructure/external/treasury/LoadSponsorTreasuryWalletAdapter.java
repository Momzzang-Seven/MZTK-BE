package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the execution-side {@link LoadSponsorTreasuryWalletPort} by
 * delegating to the treasury module's {@link LoadTreasuryWalletByRoleUseCase} with the {@code
 * SPONSOR} role.
 *
 * <p>Bean name is namespaced ({@code executionLoadSponsorTreasuryWalletAdapter}) to avoid a
 * collision with the transaction module's {@code LoadRewardTreasuryWalletAdapter} when both are
 * scanned together.
 */
@Component("executionLoadSponsorTreasuryWalletAdapter")
@RequiredArgsConstructor
public class LoadSponsorTreasuryWalletAdapter implements LoadSponsorTreasuryWalletPort {

  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Override
  public Optional<TreasuryWalletInfo> load() {
    return loadTreasuryWalletByRoleUseCase
        .execute(TreasuryRole.SPONSOR)
        .map(LoadSponsorTreasuryWalletAdapter::toInfo);
  }

  private static TreasuryWalletInfo toInfo(TreasuryWalletView view) {
    return new TreasuryWalletInfo(
        view.walletAlias(),
        view.kmsKeyId(),
        view.walletAddress(),
        view.status() == TreasuryWalletStatus.ACTIVE);
  }
}
