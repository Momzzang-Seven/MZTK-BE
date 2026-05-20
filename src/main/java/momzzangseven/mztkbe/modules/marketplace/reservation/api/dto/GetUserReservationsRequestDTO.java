package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;

/** Query DTO for the current user's reservation list. */
public record GetUserReservationsRequestDTO(
    ReservationListStatusFilter status, String cursor, Integer size) {

  public GetUserReservationsQuery toQuery(Long userId) {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(cursor, size, 20, 100, GetUserReservationsQuery.cursorScope(status));
    return new GetUserReservationsQuery(userId, status, pageRequest);
  }
}
