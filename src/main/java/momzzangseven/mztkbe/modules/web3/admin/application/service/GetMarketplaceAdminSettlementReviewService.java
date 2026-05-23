package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;

@RequiredArgsConstructor
public class GetMarketplaceAdminSettlementReviewService
    implements GetMarketplaceAdminSettlementReviewUseCase {

  private final GetMarketplaceAdminSettlementReviewPort getMarketplaceAdminSettlementReviewPort;
  private final ResolveMarketplaceAdminAuthorityPort resolveMarketplaceAdminAuthorityPort;

  @Override
  public GetMarketplaceAdminSettlementReviewResult execute(
      GetMarketplaceAdminSettlementReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    MarketplaceAdminAuthorityView authority = resolveMarketplaceAdminAuthorityPort.resolve();
    return getMarketplaceAdminSettlementReviewPort.getSettlementReview(
        query.reservationId(), authority.canEarlySettle());
  }
}
