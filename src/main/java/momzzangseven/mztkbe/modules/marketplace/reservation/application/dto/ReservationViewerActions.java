package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Viewer-scoped marketplace reservation actions exposed at the top level of read responses. */
public record ReservationViewerActions(
    String viewerAction,
    boolean viewerCanCancel,
    boolean viewerCanReject,
    boolean viewerCanApprove,
    boolean viewerCanComplete,
    boolean viewerCanClaimDeadlineRefund,
    boolean viewerCanRecover) {

  public static ReservationViewerActions none() {
    return new ReservationViewerActions(null, false, false, false, false, false, false);
  }
}
