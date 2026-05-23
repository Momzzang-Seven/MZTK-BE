package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrepareMarketplaceUserExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.BuildMarketplaceUserExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;

/** Coordinates marketplace user execution prepare through marketplace-owned ports. */
@RequiredArgsConstructor
public class PrepareMarketplaceUserExecutionService
    implements PrepareMarketplaceUserExecutionUseCase {

  private final BuildMarketplaceUserExecutionDraftPort buildDraftPort;
  private final SubmitMarketplaceExecutionDraftPort submitDraftPort;

  @Override
  public MarketplaceExecutionIntentResult prepare(MarketplaceEscrowExecutionRequest request) {
    MarketplaceExecutionDraft draft = buildDraftPort.build(request);
    return submitDraftPort.submit(draft);
  }
}
