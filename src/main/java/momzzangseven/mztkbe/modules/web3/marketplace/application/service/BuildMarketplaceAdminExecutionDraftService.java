package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.BuildMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceAdminExecutionDraftPort;

@RequiredArgsConstructor
public class BuildMarketplaceAdminExecutionDraftService
    implements BuildMarketplaceAdminExecutionDraftUseCase {

  private final BuildMarketplaceAdminExecutionDraftPort buildMarketplaceAdminExecutionDraftPort;

  @Override
  public MarketplaceExecutionDraft execute(MarketplaceAdminEscrowExecutionRequest request) {
    return buildMarketplaceAdminExecutionDraftPort.build(request);
  }
}
