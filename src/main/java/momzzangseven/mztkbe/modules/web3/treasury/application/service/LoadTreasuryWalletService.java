package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service: loads a {@code TreasuryWallet} and projects it onto {@link
 * TreasuryWalletView}.
 *
 * <p>Annotated {@link AdminOnly} so the {@code AdminOnlyAspect} enforces the {@code ROLE_ADMIN}
 * authority and records an {@code admin_action_audits} row for every read — exposing {@code
 * kmsKeyId}, address and status to a non-admin would leak operational metadata, and missing audit
 * history would diverge from the provision / disable / archive flows.
 */
@Service
@RequiredArgsConstructor
public class LoadTreasuryWalletService implements LoadTreasuryWalletUseCase {

  private final LoadTreasuryWalletPort loadTreasuryWalletPort;

  @Override
  @Transactional(readOnly = true)
  @AdminOnly(
      actionType = "TREASURY_KEY_VIEW",
      targetType = AuditTargetType.TREASURY_KEY,
      operatorId = "#operatorUserId",
      targetId = "#walletAlias")
  public Optional<TreasuryWalletView> execute(String walletAlias, Long operatorUserId) {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    return loadTreasuryWalletPort.loadByAlias(walletAlias).map(TreasuryWalletView::from);
  }
}
