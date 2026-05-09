package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.util.List;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;

/**
 * HTTP response wrapper for cursor-paginated reservation list endpoints.
 *
 * <p>Used by both:
 *
 * <ul>
 *   <li>{@code GET /marketplace/me/reservations}
 *   <li>{@code GET /marketplace/trainer/reservations}
 * </ul>
 *
 * @param reservations the items for this page
 * @param hasNext true if more pages are available
 * @param nextCursor opaque cursor token; pass as {@code ?cursor=} on the next request; null on last
 *     page
 */
public record ReservationCursorResponse(
    List<ReservationSummaryResponseDTO> reservations, boolean hasNext, String nextCursor) {

  public static ReservationCursorResponse from(CursorSlice<ReservationSummaryResult> slice) {
    List<ReservationSummaryResponseDTO> items =
        slice.items().stream().map(ReservationSummaryResponseDTO::from).toList();
    return new ReservationCursorResponse(items, slice.hasNext(), slice.nextCursor());
  }
}
