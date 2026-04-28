package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service: loads a {@code TreasuryWallet} and projects it onto {@link
 * TreasuryWalletView}.
 */
@Service
@RequiredArgsConstructor
public class LoadTreasuryWalletService implements LoadTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<TreasuryWalletView> execute(String walletAlias) {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    return loadTreasuryWalletPort.loadByAlias(walletAlias).map(TreasuryWalletView::from);
  }
}
