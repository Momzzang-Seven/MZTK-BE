package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadTreasuryWalletByRoleService implements LoadTreasuryWalletByRoleUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(readOnly = true)
  public Optional<TreasuryWalletView> execute(TreasuryRole role) {
    return loadTreasuryWalletPort.loadByAlias(role.toAlias()).map(TreasuryWalletView::from);
  }
}
