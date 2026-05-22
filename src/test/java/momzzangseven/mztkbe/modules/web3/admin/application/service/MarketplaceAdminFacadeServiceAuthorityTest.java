package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminAuthorityView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminSettlementReason;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminRefundPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ForceMarketplaceAdminSettlementPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminRefundReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.GetMarketplaceAdminSettlementReviewPort;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.ResolveMarketplaceAdminAuthorityPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Marketplace admin facade service authority boundary")
class MarketplaceAdminFacadeServiceAuthorityTest {

  @Mock private ResolveMarketplaceAdminAuthorityPort resolveMarketplaceAdminAuthorityPort;
  @Mock private GetMarketplaceAdminRefundReviewPort getRefundReviewPort;
  @Mock private GetMarketplaceAdminSettlementReviewPort getSettlementReviewPort;
  @Mock private ForceMarketplaceAdminRefundPort forceRefundPort;
  @Mock private ForceMarketplaceAdminSettlementPort forceSettlementPort;

  @Test
  @DisplayName("refund review 는 resolved authority 의 canManualRefund 를 하위 port 로 전달한다")
  void refundReview_usesResolvedManualRefundAuthority() {
    when(resolveMarketplaceAdminAuthorityPort.resolve())
        .thenReturn(MarketplaceAdminAuthorityView.elevatedAdmin());
    var service =
        new GetMarketplaceAdminRefundReviewService(
            getRefundReviewPort, resolveMarketplaceAdminAuthorityPort);

    service.execute(new GetMarketplaceAdminRefundReviewQuery(77L));

    verify(getRefundReviewPort).getRefundReview(77L, true);
  }

  @Test
  @DisplayName("settlement review 는 resolved authority 의 canEarlySettle 을 하위 port 로 전달한다")
  void settlementReview_usesResolvedEarlySettleAuthority() {
    when(resolveMarketplaceAdminAuthorityPort.resolve())
        .thenReturn(MarketplaceAdminAuthorityView.standardAdmin());
    var service =
        new GetMarketplaceAdminSettlementReviewService(
            getSettlementReviewPort, resolveMarketplaceAdminAuthorityPort);

    service.execute(new GetMarketplaceAdminSettlementReviewQuery(77L));

    verify(getSettlementReviewPort).getSettlementReview(77L, false);
  }

  @Test
  @DisplayName("refund execute 는 command 값이 아니라 resolved authority 로 manual override 를 결정한다")
  void refundExecute_usesResolvedManualRefundAuthority() {
    when(resolveMarketplaceAdminAuthorityPort.resolve())
        .thenReturn(MarketplaceAdminAuthorityView.elevatedAdmin());
    var service =
        new ForceMarketplaceAdminRefundService(
            forceRefundPort, resolveMarketplaceAdminAuthorityPort);

    service.execute(
        new ForceMarketplaceAdminRefundCommand(
            9L, 77L, MarketplaceAdminRefundReason.ADMIN_MANUAL_REFUND, "memo", true));

    verify(forceRefundPort)
        .refund(9L, 77L, MarketplaceAdminRefundReason.ADMIN_MANUAL_REFUND, "memo", true, true);
  }

  @Test
  @DisplayName("settlement execute 는 command 값이 아니라 resolved authority 로 early settle 을 결정한다")
  void settlementExecute_usesResolvedEarlySettleAuthority() {
    when(resolveMarketplaceAdminAuthorityPort.resolve())
        .thenReturn(MarketplaceAdminAuthorityView.standardAdmin());
    var service =
        new ForceMarketplaceAdminSettlementService(
            forceSettlementPort, resolveMarketplaceAdminAuthorityPort);

    service.execute(
        new ForceMarketplaceAdminSettlementCommand(
            9L, 77L, MarketplaceAdminSettlementReason.ADMIN_MANUAL_SETTLE, "memo", true));

    verify(forceSettlementPort)
        .settle(9L, 77L, MarketplaceAdminSettlementReason.ADMIN_MANUAL_SETTLE, "memo", true, false);
  }
}
