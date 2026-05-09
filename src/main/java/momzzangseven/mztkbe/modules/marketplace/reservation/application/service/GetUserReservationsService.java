package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a cursor-paginated list of reservations for the authenticated user.
 *
 * <h2>Pagination</h2>
 *
 * <p>Uses keyset pagination on {@code (reservationDate DESC, id DESC)}. The probe pattern ({@code
 * size + 1}) is used to determine {@code hasNext} without an extra COUNT query. The resulting
 * {@code nextCursor} token is Base64-encoded and opaque to the client.
 *
 * <h2>Enrichment strategy</h2>
 *
 * <ul>
 *   <li>{@code classTitle} — snapshot-first: reads {@code bookedClassTitle} for new records; falls
 *       back to the cross-module adapter for legacy records ({@code bookedPriceAmount == null}).
 *   <li>{@code thumbnailFinalObjectKey} — live lookup (no snapshot; absent for inactive classes).
 *   <li>{@code trainerNickname} — batch-loaded in one query for all distinct trainer IDs on the
 *       page.
 *   <li>{@code userNickname} — intentionally {@code null} on the user-list path (self-view).
 * </ul>
 *
 * <h2>Cursor sort contract</h2>
 *
 * <p>Sort order is {@code (reservationDate DESC, reservationTime DESC, id DESC)}. The cursor token
 * encodes this as {@code KeysetCursor(createdAt = reservationDate.atTime(reservationTime), id)}.
 * Using the full datetime (not {@code atStartOfDay()}) ensures that same-date reservations are
 * paginated in the same time-descending order the user sees in the list.
 */
@Service
@RequiredArgsConstructor
public class GetUserReservationsService implements GetUserReservationsUseCase {

  private static final String CURSOR_SCOPE = GetUserReservationsQuery.CURSOR_SCOPE;

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;

  @Override
  @Transactional(readOnly = true)
  public CursorSlice<ReservationSummaryResult> execute(GetUserReservationsQuery query) {
    query.validate();
    CursorPageRequest pageRequest = query.pageRequest();

    // Fetch size+1 rows to determine hasNext without a COUNT query.
    List<Reservation> loaded =
        loadReservationPort.findByUserIdCursor(query.userId(), query.status(), pageRequest);

    boolean hasNext = loaded.size() > pageRequest.size();
    List<Reservation> page = hasNext ? loaded.subList(0, pageRequest.size()) : loaded;

    if (page.isEmpty()) {
      return new CursorSlice<>(List.of(), false, null);
    }

    // Bulk-load class summaries and trainer nicknames for the current page only.
    List<Long> slotIds = page.stream().map(Reservation::getSlotId).toList();
    Map<Long, ClassSummary> classSummaries = loadClassSummaryPort.findBySlotIds(slotIds);

    List<Long> trainerIds = page.stream().map(Reservation::getTrainerId).distinct().toList();
    Map<Long, UserSummary> trainerSummaries = loadUserSummaryPort.findByIds(trainerIds);

    List<ReservationSummaryResult> items =
        page.stream()
            .map(
                r -> {
                  ClassSummary cs = classSummaries.get(r.getSlotId());
                  String classTitle =
                      r.getBookedPriceAmount() != null
                          ? r.getBookedClassTitle()
                          : (cs != null ? cs.title() : null);
                  Integer priceAmount =
                      r.getBookedPriceAmount() != null
                          ? r.getBookedPriceAmount()
                          : (cs != null ? cs.priceAmount() : null);
                  UserSummary ts = trainerSummaries.get(r.getTrainerId());
                  return ReservationSummaryResult.from(
                      r,
                      classTitle,
                      priceAmount,
                      cs != null ? cs.thumbnailFinalObjectKey() : null,
                      ts != null ? ts.nickname() : null,
                      null); // userNickname not needed on user-list path
                })
            .toList();

    String nextCursor =
        hasNext
            ? CursorCodec.encode(
                new KeysetCursor(
                    page.get(page.size() - 1)
                        .getReservationDate()
                        .atTime(page.get(page.size() - 1).getReservationTime()),
                    page.get(page.size() - 1).getId(),
                    CURSOR_SCOPE))
            : null;

    return new CursorSlice<>(items, hasNext, nextCursor);
  }
}
