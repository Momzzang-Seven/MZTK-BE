package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;

/** Path DTO for reservation detail lookups. */
public record GetReservationDetailRequestDTO(Long reservationId) {

  public GetReservationQuery toQuery(Long requesterId) {
    return new GetReservationQuery(reservationId, requesterId);
  }
}
