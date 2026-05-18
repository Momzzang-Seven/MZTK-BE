package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Reservation-owned view of a marketplace execution cleanup candidate. */
public record ReservationExecutionCleanupProtectionQuery(
    String publicId, String resourceId, String actionType, String payloadSnapshotJson) {

  public ReservationExecutionCleanupProtectionQuery(
      String publicId, String resourceId, String actionType) {
    this(publicId, resourceId, actionType, null);
  }
}
