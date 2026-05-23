package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.SubmitMarketplaceAdminExecutionDraftUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceAdminExecutionDraftPort;

@RequiredArgsConstructor
public class SubmitMarketplaceAdminExecutionDraftService
    implements SubmitMarketplaceAdminExecutionDraftUseCase {

  private final SubmitMarketplaceAdminExecutionDraftPort submitMarketplaceAdminExecutionDraftPort;

  @Override
  public MarketplaceAdminExecutionDraftSubmitResult execute(MarketplaceExecutionDraft draft) {
    return submitMarketplaceAdminExecutionDraftPort.submit(draft);
  }
}
