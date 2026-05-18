package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationViewerActions;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationViewerActionPolicyTest {

  @Test
  @DisplayName("buyer pending reservation exposes buyer cancel top-level action")
  void buyerPendingReservationExposesCancel() {
    ReservationViewerActions actions =
        ReservationViewerActionPolicy.resolve(reservation(ReservationStatus.PENDING), 1L, null);

    assertThat(actions.viewerAction()).isEqualTo("BUYER_CANCEL");
    assertThat(actions.viewerCanCancel()).isTrue();
    assertThat(actions.viewerCanReject()).isFalse();
  }

  @Test
  @DisplayName("trainer pending reservation exposes trainer reject top-level action")
  void trainerPendingReservationExposesReject() {
    ReservationViewerActions actions =
        ReservationViewerActionPolicy.resolve(reservation(ReservationStatus.PENDING), 2L, null);

    assertThat(actions.viewerAction()).isEqualTo("TRAINER_REJECT");
    assertThat(actions.viewerCanReject()).isTrue();
    assertThat(actions.viewerCanCancel()).isFalse();
  }

  @Test
  @DisplayName("buyer approved reservation exposes confirm top-level action")
  void buyerApprovedReservationExposesConfirm() {
    ReservationViewerActions actions =
        ReservationViewerActionPolicy.resolve(reservation(ReservationStatus.APPROVED), 1L, null);

    assertThat(actions.viewerAction()).isEqualTo("CONFIRM");
    assertThat(actions.viewerCanComplete()).isTrue();
  }

  @Test
  @DisplayName("active hydrated execution overrides derived stable action")
  void activeExecutionOverridesDerivedAction() {
    ReservationExecutionResumeView activeCancel =
        resumeView("MARKETPLACE_CLASS_CANCEL", "BUYER_CANCEL", true, false);

    ReservationViewerActions actions =
        ReservationViewerActionPolicy.resolve(
            reservation(ReservationStatus.PENDING), 1L, activeCancel);

    assertThat(actions.viewerAction()).isEqualTo("BUYER_CANCEL");
    assertThat(actions.viewerCanCancel()).isTrue();
    assertThat(actions.viewerCanRecover()).isFalse();
  }

  @Test
  @DisplayName("non participant receives no top-level marketplace action")
  void nonParticipantReceivesNoAction() {
    ReservationViewerActions actions =
        ReservationViewerActionPolicy.resolve(reservation(ReservationStatus.PENDING), 99L, null);

    assertThat(actions).isEqualTo(ReservationViewerActions.none());
  }

  private Reservation reservation(ReservationStatus status) {
    return Reservation.builder()
        .id(10L)
        .userId(1L)
        .trainerId(2L)
        .slotId(3L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(status)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .build();
  }

  private ReservationExecutionResumeView resumeView(
      String actionType, String viewerAction, boolean viewerCanExecute, boolean viewerCanRecover) {
    return new ReservationExecutionResumeView(
        new ReservationExecutionResumeView.Resource("ORDER", "10", "PENDING_EXECUTION"),
        actionType,
        new ReservationExecutionResumeView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 5, 18, 10, 5), 1L),
        new ReservationExecutionResumeView.Execution("EIP7702", 1),
        null,
        viewerAction,
        viewerCanExecute,
        viewerCanRecover);
  }
}
