package momzzangseven.mztkbe.modules.marketplace.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import org.junit.jupiter.api.Test;

class ReservationEscrowExecutionResultTest {

  @Test
  void command_preservesReservationOwnedSnapshots() {
    LocalDateTime sessionEndAt = LocalDateTime.parse("2026-05-20T11:00:00");

    PrepareReservationEscrowCommand command =
        new PrepareReservationEscrowCommand(
            123L, 7L, 7L, 9L, 3L, "0xbuyer", "0xtrainer", 50000, sessionEndAt);

    assertThat(command.reservationId()).isEqualTo(123L);
    assertThat(command.requesterUserId()).isEqualTo(7L);
    assertThat(command.buyerUserId()).isEqualTo(7L);
    assertThat(command.trainerUserId()).isEqualTo(9L);
    assertThat(command.reservationVersion()).isEqualTo(3L);
    assertThat(command.buyerWalletAddress()).isEqualTo("0xbuyer");
    assertThat(command.trainerWalletAddress()).isEqualTo("0xtrainer");
    assertThat(command.bookedPriceAmountKrw()).isEqualTo(50000);
    assertThat(command.sessionEndAt()).isEqualTo(sessionEndAt);
  }

  @Test
  void command_rejectsOrderKeyAndTokenWeiOwnershipByShape() {
    assertThat(PrepareReservationEscrowCommand.class.getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("orderKey", "tokenAddress", "priceAmountWei")
        .contains("bookedPriceAmountKrw");
  }

  @Test
  void command_rejectsInvalidRequiredFields() {
    LocalDateTime sessionEndAt = LocalDateTime.parse("2026-05-20T11:00:00");

    assertThatThrownBy(
            () ->
                new PrepareReservationEscrowCommand(
                    0L, 7L, 7L, 9L, 3L, "0xbuyer", "0xtrainer", 50000, sessionEndAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reservationId");
    assertThatThrownBy(
            () ->
                new PrepareReservationEscrowCommand(
                    123L, 7L, 7L, 9L, -1L, "0xbuyer", "0xtrainer", 50000, sessionEndAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reservationVersion");
    assertThatThrownBy(
            () ->
                new PrepareReservationEscrowCommand(
                    123L, 7L, 7L, 9L, 3L, " ", "0xtrainer", 50000, sessionEndAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("buyerWalletAddress");
    assertThatThrownBy(
            () ->
                new PrepareReservationEscrowCommand(
                    123L, 7L, 7L, 9L, 3L, "0xbuyer", "0xtrainer", 0, sessionEndAt))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bookedPriceAmountKrw");
  }

  @Test
  void resultWrapsReservationOwnedWeb3View() {
    ReservationExecutionWriteView view = web3View(signRequest(), null);

    PrepareReservationEscrowResult result = new PrepareReservationEscrowResult(view);

    assertThat(result.web3().resource().type()).isEqualTo("ORDER");
    assertThat(result.web3().resource().id()).isEqualTo("123");
    assertThat(result.web3().actionType()).isEqualTo("MARKETPLACE_CLASS_PURCHASE");
    assertThat(result.web3().signRequest()).isNotNull();
    assertThat(result.web3().signRequestUnavailableReason()).isNull();
  }

  @Test
  void writeViewAllowsTransactionNullForEip7702() {
    ReservationExecutionWriteView.SignRequest signRequest =
        new ReservationExecutionWriteView.SignRequest(
            new ReservationExecutionWriteView.Authorization(10L, "0xdelegate", 12L, "0xpayload"),
            new ReservationExecutionWriteView.Submit("0xdigest", 1_768_224_000L),
            null);

    ReservationExecutionWriteView view = web3View(signRequest, null);

    assertThat(view.signRequest().transaction()).isNull();
  }

  @Test
  void writeViewRejectsBothSignRequestAndUnavailableReason() {
    assertThatThrownBy(() -> web3View(signRequest(), "KMS_UNAVAILABLE"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot both be present");
  }

  private static ReservationExecutionWriteView web3View(
      ReservationExecutionWriteView.SignRequest signRequest, String unavailableReason) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "123", "PENDING_EXECUTION"),
        "MARKETPLACE_CLASS_PURCHASE",
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.parse("2026-05-20T10:05:00")),
        new ReservationExecutionWriteView.Execution("EIP7702", 2),
        signRequest,
        unavailableReason,
        false);
  }

  private static ReservationExecutionWriteView.SignRequest signRequest() {
    return new ReservationExecutionWriteView.SignRequest(
        new ReservationExecutionWriteView.Authorization(10L, "0xdelegate", 12L, "0xpayload"),
        new ReservationExecutionWriteView.Submit("0xdigest", 1_768_224_000L),
        null);
  }
}
