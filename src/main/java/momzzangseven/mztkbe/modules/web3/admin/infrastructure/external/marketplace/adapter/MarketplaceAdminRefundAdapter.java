package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.marketplace.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CalculateMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ExecuteMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CalculateMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminRefundReviewPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
public class MarketplaceAdminRefundAdapter
    implements GetMarketplaceAdminRefundReviewPort, ForceMarketplaceAdminRefundPort {

  private final CalculateMarketplaceAdminRefundReviewUseCase calculateReviewUseCase;
  private final ExecuteMarketplaceAdminRefundUseCase executeRefundUseCase;

  @Override
  public GetMarketplaceAdminRefundReviewResult getRefundReview(
      Long reservationId, boolean canManualRefund) {
    return new GetMarketplaceAdminRefundReviewResult(
        calculateReviewUseCase.execute(
            new CalculateMarketplaceAdminRefundReviewQuery(reservationId, canManualRefund)));
  }

  @Override
  public ForceMarketplaceAdminRefundResult refund(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminRefundReason reasonCode,
      String memo,
      boolean confirmManualRefund,
      boolean canManualRefund) {
    return new ForceMarketplaceAdminRefundResult(
        executeRefundUseCase.execute(
            new ExecuteMarketplaceAdminRefundCommand(
                operatorId,
                reservationId,
                MarketplaceAdminRefundReasonCode.valueOf(reasonCode.name()),
                memo,
                confirmManualRefund,
                canManualRefund)));
  }
}
