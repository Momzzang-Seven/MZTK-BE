package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminParticipantView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminResultPreview;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminTokenView;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Marketplace admin escrow DTO")
class MarketplaceAdminEscrowDtoTest {

  @Test
  @DisplayName("refund request maps operator/reservation/reason/memo/confirmation into command")
  void refundRequest_toCommand() {
    var request =
        new ForceMarketplaceAdminRefundRequestDTO(
            MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND, " evidence ", true);

    var command = request.toCommand(9L, 77L);

    assertThat(command.operatorId()).isEqualTo(9L);
    assertThat(command.reservationId()).isEqualTo(77L);
    assertThat(command.reasonCode())
        .isEqualTo(MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND);
    assertThat(command.memo()).isEqualTo(" evidence ");
    assertThat(command.confirmManualRefund()).isTrue();
  }

  @Test
  @DisplayName("settlement request maps operator/reservation/reason/memo/confirmation into command")
  void settlementRequest_toCommand() {
    var request =
        new ForceMarketplaceAdminSettlementRequestDTO(
            MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE, " evidence ", true);

    var command = request.toCommand(9L, 77L);

    assertThat(command.operatorId()).isEqualTo(9L);
    assertThat(command.reservationId()).isEqualTo(77L);
    assertThat(command.reasonCode())
        .isEqualTo(MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE);
    assertThat(command.memo()).isEqualTo(" evidence ");
    assertThat(command.confirmEarlySettle()).isTrue();
  }

  @Test
  @DisplayName("review response exposes polling and operator UX fields")
  void reviewResponse_from() {
    var response = MarketplaceAdminEscrowReviewResponseDTO.from(sampleReview());

    assertThat(response.reservationId()).isEqualTo(77L);
    assertThat(response.adminExecutionPhase()).isEqualTo(MarketplaceAdminExecutionPhase.IDLE);
    assertThat(response.nextPollAfterMs()).isEqualTo(2_000L);
    assertThat(response.pollingEndpoint())
        .isEqualTo("/admin/web3/marketplace/reservations/77/refund-review");
    assertThat(response.authority().requiresUserSignature()).isFalse();
    assertThat(response.reasonOptions()).hasSize(1);
  }

  @Test
  @DisplayName("execute response exposes admin execution phase and polling hint")
  void executeResponse_from() {
    var response = ForceMarketplaceAdminExecutionResponseDTO.from(sampleExecution());

    assertThat(response.actionType()).isEqualTo("MARKETPLACE_ADMIN_REFUND");
    assertThat(response.adminExecutionPhase())
        .isEqualTo(MarketplaceAdminExecutionPhase.QUEUED_FOR_SERVER_RELAYER);
    assertThat(response.nextPollAfterMs()).isEqualTo(2_000L);
    assertThat(response.pollingEndpoint())
        .isEqualTo("/admin/web3/marketplace/reservations/77/refund-review");
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
        List.of(
            MarketplaceAdminReviewValidationItem.info(
                momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                    .MarketplaceAdminReviewValidationCode.OK,
                "ok")),
        List.of(
            new momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                .MarketplaceAdminReasonReviewOption(
                MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT.name(),
                true,
                null,
                false,
                null,
                null,
                true,
                "MARKETPLACE_ADMIN_REFUND_TRAINER_TIMEOUT",
                new MarketplaceAdminResultPreview(
                    ReservationStatus.TIMEOUT_CANCELLED,
                    ReservationEscrowStatus.REFUNDED,
                    ReservationTerminalResolvedBy.ADMIN,
                    MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT.name()),
                List.of())));
  }

  private MarketplaceAdminExecutionResult sampleExecution() {
    return new MarketplaceAdminExecutionResult(
        77L,
        "MARKETPLACE_ADMIN_REFUND",
        "0xorder",
        ReservationStatus.ADMIN_REFUND_PENDING,
        ReservationEscrowStatus.ADMIN_REFUND_PENDING,
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
