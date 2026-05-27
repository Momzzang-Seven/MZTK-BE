package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ListTreasuryWalletsUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service backing the admin "list treasury wallets" endpoint. Maps every aggregate the
 * persistence port returns onto {@link TreasuryWalletView}; the view recovers {@code TreasuryRole}
 * from the alias so the response carries the functional label alongside the raw alias.
 *
 * <p>Intentionally <em>not</em> annotated {@code @AdminOnly}: the endpoint is a plain GET and one
 * audit row per call would just dilute {@code admin_action_audits}. Access control is enforced
 * upstream by {@code SecurityConfig} ({@code ROLE_ADMIN} on the matching request mapping); only the
 * mutating provision / disable / archive flows keep audit rows.
 */
@Service
@RequiredArgsConstructor
public class ListTreasuryWalletsService implements ListTreasuryWalletsUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(readOnly = true)
  public List<TreasuryWalletView> execute(TreasuryWalletStatus statusFilter) {
    return loadTreasuryWalletPort.loadAll(Optional.ofNullable(statusFilter)).stream()
        .map(TreasuryWalletView::from)
        .toList();
  }
}
