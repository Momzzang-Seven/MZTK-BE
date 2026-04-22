package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Query for fetching a trainer's incoming reservation list.
 *
 * @param trainerId the authenticated trainer's ID
 * @param status optional status filter; null means all statuses
 */
public record GetTrainerReservationsQuery(Long trainerId, ReservationStatus status) {

  public void validate() {
    if (trainerId == null || trainerId <= 0)
      throw new IllegalArgumentException("trainerId must be a positive number");
  }
}
