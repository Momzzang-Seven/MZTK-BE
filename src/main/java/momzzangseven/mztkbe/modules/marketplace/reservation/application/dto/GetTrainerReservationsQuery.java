package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Query for fetching a trainer's incoming reservation list.
 *
 * @param trainerId the authenticated trainer's ID
 * @param status optional status filter; null means all statuses
 */
public record GetTrainerReservationsQuery(Long trainerId, ReservationStatus status) {}
