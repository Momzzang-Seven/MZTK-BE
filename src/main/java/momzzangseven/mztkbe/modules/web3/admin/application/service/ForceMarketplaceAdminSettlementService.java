package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;

@RequiredArgsConstructor
public class ForceMarketplaceAdminSettlementService
    implements ForceMarketplaceAdminSettlementUseCase {

  private final ForceMarketplaceAdminSettlementPort forceMarketplaceAdminSettlementPort;
  private final ResolveMarketplaceAdminAuthorityPort resolveMarketplaceAdminAuthorityPort;

  @Override
  public ForceMarketplaceAdminSettlementResult execute(
      ForceMarketplaceAdminSettlementCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    MarketplaceAdminAuthorityView authority = resolveMarketplaceAdminAuthorityPort.resolve();
    return forceMarketplaceAdminSettlementPort.settle(
        command.operatorId(),
        command.reservationId(),
        command.reasonCode(),
        command.memo(),
        command.confirmEarlySettle(),
        authority.canEarlySettle());
  }
}
