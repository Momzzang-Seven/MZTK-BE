package momzzangseven.mztkbe.modules.web3.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;

@RequiredArgsConstructor
public class GetMarketplaceAdminRefundReviewService
    implements GetMarketplaceAdminRefundReviewUseCase {

  private final GetMarketplaceAdminRefundReviewPort getMarketplaceAdminRefundReviewPort;
  private final ResolveMarketplaceAdminAuthorityPort resolveMarketplaceAdminAuthorityPort;

  @Override
  public GetMarketplaceAdminRefundReviewResult execute(GetMarketplaceAdminRefundReviewQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }
    query.validate();
    MarketplaceAdminAuthorityView authority = resolveMarketplaceAdminAuthorityPort.resolve();
    return getMarketplaceAdminRefundReviewPort.getRefundReview(
        query.reservationId(), authority.canManualRefund());
  }
}
