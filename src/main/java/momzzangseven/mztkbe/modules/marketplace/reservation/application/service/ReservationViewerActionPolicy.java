package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationViewerActions;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

public final class ReservationViewerActionPolicy {

  private ReservationViewerActionPolicy() {}

  public static ReservationViewerActions resolve(
      Reservation reservation, Long viewerId, ReservationExecutionResumeView web3Execution) {
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
    if (buyer && status == ReservationDisplayStatus.APPROVED) {
      return new ReservationViewerActions("CONFIRM", false, false, true, false, false);
    }
    if (buyer && status == ReservationDisplayStatus.PENDING) {
      return new ReservationViewerActions("BUYER_CANCEL", true, false, false, false, false);
    }
    if (trainer && status == ReservationDisplayStatus.PENDING) {
      return new ReservationViewerActions("TRAINER_REJECT", false, true, false, false, false);
    }
    if (status == ReservationDisplayStatus.DEADLINE_RECOVERY_REQUIRED
        || status == ReservationDisplayStatus.DEADLINE_SYNC_REQUIRED) {
      return new ReservationViewerActions("RECOVER", false, false, false, false, true);
    }
    return ReservationViewerActions.none();
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
