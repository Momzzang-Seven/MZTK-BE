package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminParticipantView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminTokenView;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceMarketplaceAdminRefundRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.api.dto.ForceMarketplaceAdminSettlementRequestDTO;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminSettlementReviewUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceAdminEscrowController")
class MarketplaceAdminEscrowControllerTest {

  @Mock private GetMarketplaceAdminRefundReviewUseCase getRefundReviewUseCase;
  @Mock private GetMarketplaceAdminSettlementReviewUseCase getSettlementReviewUseCase;
  @Mock private ForceMarketplaceAdminRefundUseCase forceRefundUseCase;
  @Mock private ForceMarketplaceAdminSettlementUseCase forceSettlementUseCase;

  private MarketplaceAdminEscrowController controller;

  @BeforeEach
  void setUp() {
    controller =
        new MarketplaceAdminEscrowController(
            getRefundReviewUseCase,
            getSettlementReviewUseCase,
            forceRefundUseCase,
            forceSettlementUseCase);
  }

  @Test
  @DisplayName("refund review delegates reservation id without reading security context")
  void getRefundReview_success() {
    given(getRefundReviewUseCase.execute(any(GetMarketplaceAdminRefundReviewQuery.class)))
        .willReturn(new GetMarketplaceAdminRefundReviewResult(sampleReview()));

    var response = controller.getRefundReview(77L);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().reservationId()).isEqualTo(77L);
    ArgumentCaptor<GetMarketplaceAdminRefundReviewQuery> captor =
        ArgumentCaptor.forClass(GetMarketplaceAdminRefundReviewQuery.class);
    verify(getRefundReviewUseCase).execute(captor.capture());
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
  }

  @Test
  @DisplayName("settlement review delegates reservation id without reading security context")
  void getSettlementReview_success() {
    given(getSettlementReviewUseCase.execute(any(GetMarketplaceAdminSettlementReviewQuery.class)))
        .willReturn(new GetMarketplaceAdminSettlementReviewResult(sampleReview()));

    var response = controller.getSettlementReview(77L);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().reservationId()).isEqualTo(77L);
    ArgumentCaptor<GetMarketplaceAdminSettlementReviewQuery> captor =
        ArgumentCaptor.forClass(GetMarketplaceAdminSettlementReviewQuery.class);
    verify(getSettlementReviewUseCase).execute(captor.capture());
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
  }

  @Test
  @DisplayName("refund execute delegates operator/reservation/reason/memo/confirmation")
  void refund_success() {
    given(forceRefundUseCase.execute(any(ForceMarketplaceAdminRefundCommand.class)))
        .willReturn(
            new ForceMarketplaceAdminRefundResult(sampleExecution("MARKETPLACE_ADMIN_REFUND")));

    var response =
        controller.refund(
            9L,
            77L,
            new ForceMarketplaceAdminRefundRequestDTO(
                MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, "memo", false));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().actionType()).isEqualTo("MARKETPLACE_ADMIN_REFUND");
    ArgumentCaptor<ForceMarketplaceAdminRefundCommand> captor =
        ArgumentCaptor.forClass(ForceMarketplaceAdminRefundCommand.class);
    verify(forceRefundUseCase).execute(captor.capture());
    assertThat(captor.getValue().operatorId()).isEqualTo(9L);
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
    assertThat(captor.getValue().reasonCode())
        .isEqualTo(MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT);
    assertThat(captor.getValue().memo()).isEqualTo("memo");
    assertThat(captor.getValue().confirmManualRefund()).isFalse();
  }

  @Test
  @DisplayName("settlement execute delegates operator/reservation/reason/memo/confirmation")
  void settle_success() {
    given(forceSettlementUseCase.execute(any(ForceMarketplaceAdminSettlementCommand.class)))
        .willReturn(
            new ForceMarketplaceAdminSettlementResult(sampleExecution("MARKETPLACE_ADMIN_SETTLE")));

    var response =
        controller.settle(
            9L,
            77L,
            new ForceMarketplaceAdminSettlementRequestDTO(
                MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE, "memo", true));

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData().actionType()).isEqualTo("MARKETPLACE_ADMIN_SETTLE");
    ArgumentCaptor<ForceMarketplaceAdminSettlementCommand> captor =
        ArgumentCaptor.forClass(ForceMarketplaceAdminSettlementCommand.class);
    verify(forceSettlementUseCase).execute(captor.capture());
    assertThat(captor.getValue().operatorId()).isEqualTo(9L);
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
    assertThat(captor.getValue().reasonCode())
        .isEqualTo(MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE);
    assertThat(captor.getValue().memo()).isEqualTo("memo");
    assertThat(captor.getValue().confirmEarlySettle()).isTrue();
  }

  @Test
  @DisplayName("execute requires authenticated operator id")
  void execute_requiresOperatorId() {
    assertThatThrownBy(
            () ->
                controller.refund(
                    null,
                    77L,
                    new ForceMarketplaceAdminRefundRequestDTO(
                        MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT, null, false)))
        .isInstanceOf(UserNotAuthenticatedException.class);
  }

  private MarketplaceAdminEscrowReviewResult sampleReview() {
    return new MarketplaceAdminEscrowReviewResult(
        77L,
        true,
        null,
        null,
        ReservationStatus.APPROVED,
        ReservationEscrowStatus.LOCKED,
        new MarketplaceAdminParticipantView(10L, "0x1111111111111111111111111111111111111111"),
        new MarketplaceAdminParticipantView(20L, "0x2222222222222222222222222222222222222222"),
        new MarketplaceAdminTokenView(
            "0x3333333333333333333333333333333333333333", java.math.BigInteger.TEN, "MZT"),
        LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        LocalDateTime.of(2026, 1, 2, 3, 4, 6),
        12L,
        MarketplaceAdminExecutionPhase.IDLE,
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        null,
        MarketplaceAdminExecutionAuthorityView.serverRelayerOnly(),
        null,
        null,
        List.of(),
        List.of());
  }

  private MarketplaceAdminExecutionResult sampleExecution(String actionType) {
    return new MarketplaceAdminExecutionResult(
        77L,
        actionType,
        "0xorder",
        actionType.equals("MARKETPLACE_ADMIN_REFUND")
            ? ReservationStatus.ADMIN_REFUND_PENDING
            : ReservationStatus.ADMIN_SETTLE_PENDING,
        actionType.equals("MARKETPLACE_ADMIN_REFUND")
            ? ReservationEscrowStatus.ADMIN_REFUND_PENDING
            : ReservationEscrowStatus.ADMIN_SETTLE_PENDING,
        new MarketplaceAdminExecutionResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 1, 2, 3, 4, 5)),
        new MarketplaceAdminExecutionResult.Execution(
            "EIP1559", false, MarketplaceAdminExecutionAuthorityView.SERVER_RELAYER_ONLY),
        MarketplaceAdminExecutionPhase.QUEUED_FOR_SERVER_RELAYER,
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        false);
  }
}
