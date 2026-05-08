package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a cursor-paginated list of reservations assigned to the authenticated trainer.
 *
 * <h2>Pagination</h2>
 *
 * <p>Uses keyset pagination on {@code (reservationDate DESC, id DESC)}. The probe pattern ({@code
 * size + 1}) determines {@code hasNext} without an extra COUNT query.
 *
 * <h2>Enrichment strategy</h2>
 *
 * <ul>
 *   <li>{@code classTitle} — snapshot-first: reads {@code bookedClassTitle} for new records; falls
 *       back to the cross-module adapter for legacy records ({@code bookedPriceAmount == null}).
 *   <li>{@code thumbnailFinalObjectKey} — live lookup (no snapshot; absent for inactive classes).
 *   <li>{@code trainerNickname} — single lookup by {@code trainerId} (all rows share one trainer).
 *   <li>{@code userNickname} — batch-loaded so the trainer can identify each booker.
 * </ul>
 *
 * <h2>Cursor sort contract</h2>
 *
 * <p>Sort order is {@code (reservationDate DESC, reservationTime DESC, id DESC)}. The cursor token
 * encodes this as {@code KeysetCursor(createdAt = reservationDate.atTime(reservationTime), id)}.
 * This matches the user-list sort contract so trainers and users see the same temporal ordering.
 */
@Service
@RequiredArgsConstructor
public class GetTrainerReservationsService implements GetTrainerReservationsUseCase {

  private static final String CURSOR_SCOPE = GetTrainerReservationsQuery.CURSOR_SCOPE;

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;

  @Override
  @Transactional(readOnly = true)
  public CursorSlice<ReservationSummaryResult> execute(GetTrainerReservationsQuery query) {
    query.validate();
    CursorPageRequest pageRequest = query.pageRequest();

    // Fetch size+1 rows to determine hasNext without a COUNT query.
    List<Reservation> loaded =
        loadReservationPort.findByTrainerIdCursor(query.trainerId(), query.status(), pageRequest);

    boolean hasNext = loaded.size() > pageRequest.size();
    List<Reservation> page = hasNext ? loaded.subList(0, pageRequest.size()) : loaded;

    if (page.isEmpty()) {
      return new CursorSlice<>(List.of(), false, null);
    }

    // Bulk-load class summaries in one JOIN query.
    List<Long> slotIds = page.stream().map(Reservation::getSlotId).toList();
    Map<Long, ClassSummary> classSummaries = loadClassSummaryPort.findBySlotIds(slotIds);

    // All reservations on the page belong to the same trainer — single lookup.
    UserSummary trainerSummary = loadUserSummaryPort.findById(query.trainerId()).orElse(null);

    // Batch-load user (booker) nicknames for the current page.
    List<Long> userIds = page.stream().map(Reservation::getUserId).distinct().toList();
    Map<Long, UserSummary> userSummaries = loadUserSummaryPort.findByIds(userIds);

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
                  UserSummary userSummary = userSummaries.get(r.getUserId());
                  return ReservationSummaryResult.from(
                      r,
                      classTitle,
                      priceAmount,
                      cs != null ? cs.thumbnailFinalObjectKey() : null,
                      trainerSummary != null ? trainerSummary.nickname() : null,
                      userSummary != null ? userSummary.nickname() : null);
                })
            .toList();

    String nextCursor =
        hasNext
            ? CursorCodec.encode(
                new KeysetCursor(
                    page.getLast().getReservationDate().atTime(page.getLast().getReservationTime()),
                    page.getLast().getId(),
                    CURSOR_SCOPE))
            : null;

    return new CursorSlice<>(items, hasNext, nextCursor);
  }
}
