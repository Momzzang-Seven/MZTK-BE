package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoadRewardTreasuryWalletAdapter implements LoadRewardTreasuryWalletPort {

  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Override
  public Optional<TreasuryWalletInfo> load() {
    return loadTreasuryWalletByRoleUseCase
        .execute(TreasuryRole.REWARD)
        .map(LoadRewardTreasuryWalletAdapter::toInfo);
  }

  private static TreasuryWalletInfo toInfo(TreasuryWalletView view) {
    return new TreasuryWalletInfo(
        view.walletAlias(),
        view.kmsKeyId(),
        view.walletAddress(),
        view.status() == TreasuryWalletStatus.ACTIVE);
  }
}
