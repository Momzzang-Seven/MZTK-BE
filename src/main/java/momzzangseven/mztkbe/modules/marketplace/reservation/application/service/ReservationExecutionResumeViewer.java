package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.Set;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

final class ReservationExecutionResumeViewer {

  private static final Set<String> RECOVERABLE_STATUSES =
      Set.of("FAILED_ONCHAIN", "EXPIRED", "CANCELED", "NONCE_STALE");

  private ReservationExecutionResumeViewer() {}

  static ReservationExecutionResumeView hydrate(
      Reservation reservation, Long viewerId, ReservationExecutionResumeView view) {
    if (view == null) {
      return null;
    }
    String viewerAction = viewerAction(reservation, view.actionType());
    boolean owner = isViewerOwner(reservation, viewerId, viewerAction);
    String status = view.executionIntent().status();
    boolean participant =
        reservation.isOwnedByUser(viewerId) || reservation.isOwnedByTrainer(viewerId);
    boolean confirmedReplayAvailable =
        participant
            && reservation.getCurrentExecutionIntentPublicId() != null
            && reservation.getCurrentExecutionIntentPublicId().equals(view.executionIntent().id())
            && "CONFIRMED".equals(status);
    return view.withViewer(
        viewerAction,
        owner && "AWAITING_SIGNATURE".equals(status),
        confirmedReplayAvailable || (owner && RECOVERABLE_STATUSES.contains(status)));
  }

  private static String viewerAction(Reservation reservation, String actionType) {
    return switch (actionType) {
      case "MARKETPLACE_CLASS_PURCHASE" -> "PURCHASE";
      case "MARKETPLACE_CLASS_CONFIRM" -> "CONFIRM";
      case "MARKETPLACE_CLASS_EXPIRED_REFUND" -> "DEADLINE_REFUND";
      case "MARKETPLACE_CLASS_CANCEL" -> cancelViewerAction(reservation);
      default -> null;
    };
  }

  private static String cancelViewerAction(Reservation reservation) {
    if (reservation.getPendingAction() == ReservationEscrowAction.TRAINER_REJECT
        || reservation.getStatus() == ReservationStatus.REJECT_PENDING
        || reservation.getStatus() == ReservationStatus.REJECTED) {
      return "TRAINER_REJECT";
    }
    if (reservation.getPendingAction() == ReservationEscrowAction.BUYER_CANCEL
        || reservation.getStatus() == ReservationStatus.CANCEL_PENDING
        || reservation.getStatus() == ReservationStatus.USER_CANCELLED) {
      return "BUYER_CANCEL";
    }
    return null;
  }

  private static boolean isViewerOwner(
      Reservation reservation, Long viewerId, String viewerAction) {
    if (viewerAction == null) {
      return false;
    }
    return switch (viewerAction) {
      case "TRAINER_REJECT" -> reservation.isOwnedByTrainer(viewerId);
      case "PURCHASE", "BUYER_CANCEL", "CONFIRM", "DEADLINE_REFUND" ->
          reservation.isOwnedByUser(viewerId);
      default -> false;
    };
  }
}
