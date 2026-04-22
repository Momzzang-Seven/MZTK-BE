package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Query for fetching a user's own reservation list.
 *
 * @param userId the authenticated user's ID
 * @param status optional status filter; null means all statuses
 */
public record GetUserReservationsQuery(Long userId, ReservationStatus status) {}
