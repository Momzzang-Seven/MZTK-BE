package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.treasury;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.LoadTreasuryWalletByRoleUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoadInternalExecutionSignerWalletAdapter
    implements LoadInternalExecutionSignerWalletPort {

  private final LoadTreasuryWalletByRoleUseCase loadTreasuryWalletByRoleUseCase;

  @Override
  public Optional<TreasuryWalletInfo> load(ExecutionActionType actionType) {
    return loadTreasuryWalletByRoleUseCase.execute(roleFor(actionType)).map(this::toInfo);
  }

  private TreasuryRole roleFor(ExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_ADMIN_REFUND, MARKETPLACE_ADMIN_SETTLE -> TreasuryRole.MARKETPLACE_SIGNER;
      default -> TreasuryRole.SPONSOR;
    };
  }

  private TreasuryWalletInfo toInfo(TreasuryWalletView view) {
    return new TreasuryWalletInfo(
        view.walletAlias(),
        view.kmsKeyId(),
        view.walletAddress(),
        view.status() == TreasuryWalletStatus.ACTIVE);
  }
}
