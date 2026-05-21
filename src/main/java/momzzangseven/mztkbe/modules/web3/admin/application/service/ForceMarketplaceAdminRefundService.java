package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;

@RequiredArgsConstructor
public class ForceMarketplaceAdminRefundService implements ForceMarketplaceAdminRefundUseCase {

  private final ForceMarketplaceAdminRefundPort forceMarketplaceAdminRefundPort;
  private final ResolveMarketplaceAdminAuthorityPort resolveMarketplaceAdminAuthorityPort;

  @Override
  public ForceMarketplaceAdminRefundResult execute(ForceMarketplaceAdminRefundCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    MarketplaceAdminAuthorityView authority = resolveMarketplaceAdminAuthorityPort.resolve();
    return forceMarketplaceAdminRefundPort.refund(
        command.operatorId(),
        command.reservationId(),
        command.reasonCode(),
        command.memo(),
        command.confirmManualRefund(),
        authority.canManualRefund());
  }
}
