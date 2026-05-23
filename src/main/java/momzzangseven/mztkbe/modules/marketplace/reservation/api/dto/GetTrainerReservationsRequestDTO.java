package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;

/** Query DTO for the trainer reservation list. */
public record GetTrainerReservationsRequestDTO(
    ReservationListStatusFilter status, String cursor, Integer size) {

  public GetTrainerReservationsQuery toQuery(Long trainerId) {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(
            cursor, size, 20, 100, GetTrainerReservationsQuery.cursorScope(status));
    return new GetTrainerReservationsQuery(trainerId, status, pageRequest);
  }
}
