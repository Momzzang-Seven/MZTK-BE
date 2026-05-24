package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminEscrowReviewView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminExecutionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminSettlementReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Marketplace admin escrow DTO")
class MarketplaceAdminEscrowDtoTest {

  @Test
  @DisplayName("refund request maps operator/reservation/reason/memo/confirmation into command")
  void refundRequest_toCommand() {
    var request =
        new ForceMarketplaceAdminRefundRequestDTO(
            MarketplaceAdminRefundReason.ADMIN_MANUAL_REFUND, " evidence ", true);

    var command = request.toCommand(9L, 77L);

    assertThat(command.operatorId()).isEqualTo(9L);
    assertThat(command.reservationId()).isEqualTo(77L);
    assertThat(command.reasonCode()).isEqualTo(MarketplaceAdminRefundReason.ADMIN_MANUAL_REFUND);
    assertThat(command.memo()).isEqualTo(" evidence ");
    assertThat(command.confirmManualRefund()).isTrue();
  }

  @Test
  @DisplayName("settlement request maps operator/reservation/reason/memo/confirmation into command")
  void settlementRequest_toCommand() {
    var request =
        new ForceMarketplaceAdminSettlementRequestDTO(
            MarketplaceAdminSettlementReason.ADMIN_MANUAL_SETTLE, " evidence ", true);

    var command = request.toCommand(9L, 77L);

    assertThat(command.operatorId()).isEqualTo(9L);
    assertThat(command.reservationId()).isEqualTo(77L);
    assertThat(command.reasonCode())
        .isEqualTo(MarketplaceAdminSettlementReason.ADMIN_MANUAL_SETTLE);
    assertThat(command.memo()).isEqualTo(" evidence ");
    assertThat(command.confirmEarlySettle()).isTrue();
  }

  @Test
  @DisplayName("review response exposes polling and operator UX fields")
  void reviewResponse_from() {
    var response = MarketplaceAdminEscrowReviewResponseDTO.from(sampleReview());

    assertThat(response.reservationId()).isEqualTo(77L);
    assertThat(response.adminExecutionPhase()).isEqualTo("IDLE");
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
    assertThat(response.adminExecutionPhase()).isEqualTo("QUEUED_FOR_SERVER_RELAYER");
    assertThat(response.nextPollAfterMs()).isEqualTo(2_000L);
    assertThat(response.pollingEndpoint())
        .isEqualTo("/admin/web3/marketplace/reservations/77/refund-review");
  }

  private MarketplaceAdminEscrowReviewView sampleReview() {
    return new MarketplaceAdminEscrowReviewView(
        77L,
        true,
        null,
        null,
        "APPROVED",
        "LOCKED",
        new MarketplaceAdminEscrowReviewView.Participant(
            10L, "0x1111111111111111111111111111111111111111"),
        new MarketplaceAdminEscrowReviewView.Participant(
            20L, "0x2222222222222222222222222222222222222222"),
        new MarketplaceAdminEscrowReviewView.Token(
            "0x3333333333333333333333333333333333333333", java.math.BigInteger.TEN, "MZT"),
        LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        LocalDateTime.of(2026, 1, 2, 3, 4, 6),
        12L,
        "IDLE",
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        null,
        new MarketplaceAdminEscrowReviewView.Authority(
            false, "SERVER_RELAYER_ONLY", false, null, false, "UNCHECKED", false, false),
        null,
        null,
        List.of(new MarketplaceAdminEscrowReviewView.ValidationItem("OK", "INFO", "ok", false)),
        List.of(
            new MarketplaceAdminEscrowReviewView.ReasonOption(
                "TRAINER_TIMEOUT",
                true,
                null,
                false,
                null,
                null,
                true,
                "MARKETPLACE_ADMIN_REFUND_TRAINER_TIMEOUT",
                new MarketplaceAdminEscrowReviewView.ResultPreview(
                    "TIMEOUT_CANCELLED", "REFUNDED", "ADMIN", "TRAINER_TIMEOUT"),
                List.of())));
  }

  private MarketplaceAdminExecutionView sampleExecution() {
    return new MarketplaceAdminExecutionView(
        77L,
        "MARKETPLACE_ADMIN_REFUND",
        "0xorder",
        "ADMIN_REFUND_PENDING",
        "ADMIN_REFUND_PENDING",
        new MarketplaceAdminExecutionView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 1, 2, 3, 4, 5)),
        new MarketplaceAdminExecutionView.Execution("EIP1559", false, "SERVER_RELAYER_ONLY"),
        "QUEUED_FOR_SERVER_RELAYER",
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        false);
  }
}
