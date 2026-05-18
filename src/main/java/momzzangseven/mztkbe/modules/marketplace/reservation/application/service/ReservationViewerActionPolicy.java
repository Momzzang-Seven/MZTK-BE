package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationViewerActions;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

public final class ReservationViewerActionPolicy {

  private ReservationViewerActionPolicy() {}

  public static ReservationViewerActions resolve(
      Reservation reservation,
      Long viewerId,
      ReservationExecutionResumeView web3Execution,
      Clock clock) {
    if (viewerId == null
        || (!reservation.isOwnedByUser(viewerId) && !reservation.isOwnedByTrainer(viewerId))) {
      return ReservationViewerActions.none();
    }

    if (web3Execution != null && web3Execution.viewerAction() != null) {
      return fromActiveExecution(web3Execution);
    }

    ReservationDisplayStatus status = ReservationDisplayStatusMapper.displayStatus(reservation);
    boolean buyer = reservation.isOwnedByUser(viewerId);
    boolean trainer = reservation.isOwnedByTrainer(viewerId);

    if (buyer && status == ReservationDisplayStatus.DEADLINE_REFUND_AVAILABLE) {
      return new ReservationViewerActions("DEADLINE_REFUND", false, false, false, true, false);
    }
    if (buyer && status == ReservationDisplayStatus.APPROVED && sessionEnded(reservation, clock)) {
      return new ReservationViewerActions("CONFIRM", false, false, true, false, false);
    }
    if (buyer && status == ReservationDisplayStatus.PENDING) {
      return new ReservationViewerActions("BUYER_CANCEL", true, false, false, false, false);
    }
    if (trainer && status == ReservationDisplayStatus.PENDING) {
      return new ReservationViewerActions("TRAINER_REJECT", false, true, false, false, false);
    }
    if (buyer
        && status == ReservationDisplayStatus.DEADLINE_RECOVERY_REQUIRED
        && deadlineExpired(reservation, clock)) {
      return new ReservationViewerActions("RECOVER", false, false, false, false, true);
    }
    return ReservationViewerActions.none();
  }

  private static boolean deadlineExpired(Reservation reservation, Clock clock) {
    return reservation.getContractDeadlineAt() != null
        && LocalDateTime.now(Objects.requireNonNull(clock, "clock"))
            .isAfter(reservation.getContractDeadlineAt());
  }

  private static boolean sessionEnded(Reservation reservation, Clock clock) {
    LocalDateTime sessionEnd =
        LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime())
            .plusMinutes(reservation.getDurationMinutes());
    return !LocalDateTime.now(Objects.requireNonNull(clock, "clock")).isBefore(sessionEnd);
  }

  private static ReservationViewerActions fromActiveExecution(
      ReservationExecutionResumeView web3Execution) {
    return switch (web3Execution.viewerAction()) {
      case "BUYER_CANCEL" ->
          new ReservationViewerActions(
              "BUYER_CANCEL",
              web3Execution.viewerCanExecute(),
              false,
              false,
              false,
              web3Execution.viewerCanRecover());
      case "TRAINER_REJECT" ->
          new ReservationViewerActions(
              "TRAINER_REJECT",
              false,
              web3Execution.viewerCanExecute(),
              false,
              false,
              web3Execution.viewerCanRecover());
      case "CONFIRM" ->
          new ReservationViewerActions(
              "CONFIRM",
              false,
              false,
              web3Execution.viewerCanExecute(),
              false,
              web3Execution.viewerCanRecover());
      case "DEADLINE_REFUND" ->
          new ReservationViewerActions(
              "DEADLINE_REFUND",
              false,
              false,
              false,
              web3Execution.viewerCanExecute(),
              web3Execution.viewerCanRecover());
      case "PURCHASE" ->
          new ReservationViewerActions(
              "PURCHASE", false, false, false, false, web3Execution.viewerCanRecover());
      default ->
          new ReservationViewerActions(
              web3Execution.viewerAction(),
              false,
              false,
              false,
              false,
              web3Execution.viewerCanRecover());
    };
  }
}
