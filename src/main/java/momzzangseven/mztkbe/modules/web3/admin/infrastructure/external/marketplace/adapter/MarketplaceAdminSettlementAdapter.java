package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.marketplace.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminSettlementReviewPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
public class MarketplaceAdminSettlementAdapter
    implements GetMarketplaceAdminSettlementReviewPort, ForceMarketplaceAdminSettlementPort {

  private final CalculateMarketplaceAdminSettlementReviewUseCase calculateReviewUseCase;
  private final ExecuteMarketplaceAdminSettlementUseCase executeSettlementUseCase;

  @Override
  public GetMarketplaceAdminSettlementReviewResult getSettlementReview(
      Long reservationId, boolean canEarlySettle) {
    return new GetMarketplaceAdminSettlementReviewResult(
        calculateReviewUseCase.execute(
            new CalculateMarketplaceAdminSettlementReviewQuery(reservationId, canEarlySettle)));
  }

  @Override
  public ForceMarketplaceAdminSettlementResult settle(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminSettleReasonCode reasonCode,
      String memo,
      boolean confirmEarlySettle,
      boolean canEarlySettle) {
    return new ForceMarketplaceAdminSettlementResult(
        executeSettlementUseCase.execute(
            new ExecuteMarketplaceAdminSettlementCommand(
                operatorId, reservationId, reasonCode, memo, confirmEarlySettle, canEarlySettle)));
  }
}
