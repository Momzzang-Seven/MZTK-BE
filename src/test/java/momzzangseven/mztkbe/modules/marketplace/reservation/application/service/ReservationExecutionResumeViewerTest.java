package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationExecutionResumeViewerTest {

  @Test
  @DisplayName("참여자는 CONFIRMED current intent를 replay recovery 가능 상태로 본다")
  void hydrate_confirmedCurrentIntent_participantCanRecover() {
    Reservation reservation =
        baseReservation().toBuilder()
            .status(ReservationStatus.REJECT_PENDING)
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .currentExecutionIntentPublicId("reject-intent-1")
            .build();
    ReservationExecutionResumeView view =
        resumeView("MARKETPLACE_CLASS_CANCEL", "CONFIRMED", "reject-intent-1");

    ReservationExecutionResumeView hydrated =
        ReservationExecutionResumeViewer.hydrate(reservation, 1L, view);

    assertThat(hydrated.viewerAction()).isEqualTo("TRAINER_REJECT");
    assertThat(hydrated.viewerCanExecute()).isFalse();
    assertThat(hydrated.viewerCanRecover()).isTrue();
  }

  @Test
  @DisplayName("참여자는 transaction SUCCEEDED current intent도 replay recovery 가능 상태로 본다")
  void hydrate_succeededTransactionCurrentIntent_participantCanRecover() {
    Reservation reservation =
        baseReservation().toBuilder()
            .status(ReservationStatus.REJECT_PENDING)
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .currentExecutionIntentPublicId("reject-intent-1")
            .build();
    ReservationExecutionResumeView view =
        resumeView("MARKETPLACE_CLASS_CANCEL", "PENDING_ONCHAIN", "reject-intent-1", "SUCCEEDED");

    ReservationExecutionResumeView hydrated =
        ReservationExecutionResumeViewer.hydrate(reservation, 1L, view);

    assertThat(hydrated.viewerAction()).isEqualTo("TRAINER_REJECT");
    assertThat(hydrated.viewerCanExecute()).isFalse();
    assertThat(hydrated.viewerCanRecover()).isTrue();
  }

  @Test
  @DisplayName("비참여자는 CONFIRMED current intent를 recovery 가능 상태로 보지 않는다")
  void hydrate_confirmedCurrentIntent_nonParticipantCannotRecover() {
    Reservation reservation =
        baseReservation().toBuilder()
            .status(ReservationStatus.REJECT_PENDING)
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .currentExecutionIntentPublicId("reject-intent-1")
            .build();
    ReservationExecutionResumeView view =
        resumeView("MARKETPLACE_CLASS_CANCEL", "CONFIRMED", "reject-intent-1");

    ReservationExecutionResumeView hydrated =
        ReservationExecutionResumeViewer.hydrate(reservation, 999L, view);

    assertThat(hydrated.viewerCanExecute()).isFalse();
    assertThat(hydrated.viewerCanRecover()).isFalse();
  }

  @Test
  @DisplayName("CONFIRMED intent라도 current pointer와 다르면 recovery 가능 상태로 보지 않는다")
  void hydrate_confirmedIntentDifferentFromCurrentPointerCannotRecover() {
    Reservation reservation =
        baseReservation().toBuilder()
            .status(ReservationStatus.REJECT_PENDING)
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .currentExecutionIntentPublicId("reject-intent-current")
            .build();
    ReservationExecutionResumeView view =
        resumeView("MARKETPLACE_CLASS_CANCEL", "CONFIRMED", "reject-intent-old");

    ReservationExecutionResumeView hydrated =
        ReservationExecutionResumeViewer.hydrate(reservation, 1L, view);

    assertThat(hydrated.viewerCanExecute()).isFalse();
    assertThat(hydrated.viewerCanRecover()).isFalse();
  }

  private Reservation baseReservation() {
    return Reservation.builder()
        .id(10L)
        .userId(1L)
        .trainerId(2L)
        .slotId(3L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .build();
  }

  private ReservationExecutionResumeView resumeView(
      String actionType, String status, String intentId) {
    return resumeView(actionType, status, intentId, null);
  }

  private ReservationExecutionResumeView resumeView(
      String actionType, String status, String intentId, String transactionStatus) {
    return new ReservationExecutionResumeView(
        new ReservationExecutionResumeView.Resource("ORDER", "10", "PENDING_EXECUTION"),
        actionType,
        new ReservationExecutionResumeView.ExecutionIntent(
            intentId, status, LocalDateTime.of(2026, 5, 18, 10, 5), 1_779_098_700L),
        new ReservationExecutionResumeView.Execution("EIP7702", 1),
        transactionStatus == null
            ? null
            : new ReservationExecutionResumeView.Transaction(99L, transactionStatus, "0xhash"));
  }
}
