package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

/**
 * Bridging adapter: implements the transaction-side {@link LoadTreasuryWalletPort} by delegating to
 * the treasury module's {@link LoadTreasuryWalletUseCase} and projecting the cross-module {@link
 * TreasuryWalletView} into the transaction-local {@link TreasuryWalletInfo} DTO.
 */
@Component
@RequiredArgsConstructor
public class LoadTreasuryWalletAdapter implements LoadTreasuryWalletPort {

  private final LoadTreasuryWalletUseCase loadTreasuryWalletUseCase;

  @Override
  public Optional<TreasuryWalletInfo> loadByAlias(String walletAlias, String workerId) {
    return loadTreasuryWalletUseCase
        .execute(walletAlias, Long.parseLong(workerId))
        .map(LoadTreasuryWalletAdapter::toInfo);
  }

  private static TreasuryWalletInfo toInfo(TreasuryWalletView view) {
    return new TreasuryWalletInfo(
        view.walletAlias(),
        view.kmsKeyId(),
        view.walletAddress(),
        view.status() == TreasuryWalletStatus.ACTIVE);
  }
}
