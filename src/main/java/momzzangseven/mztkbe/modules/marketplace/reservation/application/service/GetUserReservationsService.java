package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

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
public class GetUserReservationsService implements GetUserReservationsUseCase {

  // CURSOR_SCOPE is now status-dependent; use GetUserReservationsQuery.cursorScope(status).

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;
  private final LoadReservationExecutionResumePort loadReservationExecutionResumePort;
  private final RepairReservationChainReadUseCase repairReservationChainReadUseCase;

  public GetUserReservationsService(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase) {
    this.loadReservationPort = loadReservationPort;
    this.loadClassSummaryPort = loadClassSummaryPort;
    this.loadUserSummaryPort = loadUserSummaryPort;
    this.loadReservationExecutionResumePort =
        loadReservationExecutionResumePort == null
            ? emptyResumePort()
            : loadReservationExecutionResumePort;
    this.repairReservationChainReadUseCase =
        repairReservationChainReadUseCase == null
            ? noOpRepairUseCase()
            : repairReservationChainReadUseCase;
  }

  public GetUserReservationsService(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort) {
    this(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        emptyResumePort(),
        noOpRepairUseCase());
  }

  @Override
  public CursorSlice<ReservationSummaryResult> execute(GetUserReservationsQuery query) {
    query.validate();
    CursorPageRequest pageRequest = query.pageRequest();

    RepairedPage repairedPage = loadRepairedPage(query, pageRequest);
    boolean hasNext = repairedPage.hasNext();
    List<Reservation> page = repairedPage.reservations();

    if (page.isEmpty()) {
      return new CursorSlice<>(List.of(), false, null);
    }

    // Bulk-load class summaries and trainer nicknames for the current page only.
    List<Long> slotIds = page.stream().map(Reservation::getSlotId).toList();
    Map<Long, ClassSummary> classSummaries = loadClassSummaryPort.findBySlotIds(slotIds);

    List<Long> trainerIds = page.stream().map(Reservation::getTrainerId).distinct().toList();
    Map<Long, UserSummary> trainerSummaries = loadUserSummaryPort.findByIds(trainerIds);
    Map<
            Long,
            momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                .ReservationExecutionResumeView>
        web3ByReservationId =
            loadReservationExecutionResumePort.loadLatestBatch(
                page.stream().map(Reservation::getId).toList());

    List<ReservationSummaryResult> items =
        page.stream()
            .map(
                r -> {
                  ClassSummary cs = classSummaries.get(r.getSlotId());
                  // Use snapshot values only when both title and price are present.
                  // A partial snapshot (one field null) falls back to live lookup to avoid
                  // returning null classTitle or priceAmount on the list response.
                  boolean hasFullSnapshot =
                      r.getBookedPriceAmount() != null && r.getBookedClassTitle() != null;
                  String classTitle =
                      hasFullSnapshot ? r.getBookedClassTitle() : (cs != null ? cs.title() : null);
                  Integer priceAmount =
                      hasFullSnapshot
                          ? r.getBookedPriceAmount()
                          : (cs != null ? cs.priceAmount() : null);
                  UserSummary ts = trainerSummaries.get(r.getTrainerId());
                  return ReservationDisplayStatusMapper.summaryResult(
                      r,
                      classTitle,
                      priceAmount,
                      cs != null ? cs.thumbnailFinalObjectKey() : null,
                      ts != null ? ts.nickname() : null,
                      null,
                      query.userId(),
                      ReservationExecutionResumeViewer.hydrate(
                          r,
                          query.userId(),
                          web3ByReservationId.get(
                              r.getId()))); // userNickname not needed on user-list path
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
                    GetUserReservationsQuery.cursorScope(query.status())))
            : null;

    return new CursorSlice<>(items, hasNext, nextCursor);
  }

  private RepairedPage loadRepairedPage(
      GetUserReservationsQuery query, CursorPageRequest initialRequest) {
    List<Reservation> matching = new ArrayList<>();
    CursorPageRequest request = initialRequest;

    while (matching.size() <= initialRequest.size()) {
      ReservationStatus storedStatus =
          ReservationListStatusFilterMapper.toStoredStatus(query.status());
      List<Reservation> loaded =
          loadReservationPort.findByUserIdCursor(query.userId(), storedStatus, request);
      if (loaded.isEmpty()) {
        break;
      }

      List<Reservation> repaired = repairReservationChainReadUseCase.repairBatch(loaded);
      for (Reservation reservation : repaired) {
        if (matchesStatus(reservation, query.status())) {
          matching.add(reservation);
          if (matching.size() > initialRequest.size()) {
            break;
          }
        }
      }

      if (matching.size() > initialRequest.size() || loaded.size() <= request.size()) {
        break;
      }
      request = nextRequestAfter(loaded.getLast(), request);
    }

    boolean hasNext = matching.size() > initialRequest.size();
    List<Reservation> page = hasNext ? matching.subList(0, initialRequest.size()) : matching;
    return new RepairedPage(List.copyOf(page), hasNext);
  }

  private static boolean matchesStatus(
      Reservation reservation, ReservationListStatusFilter status) {
    return ReservationListStatusFilterMapper.matchesDisplayStatus(reservation, status);
  }

  private static CursorPageRequest nextRequestAfter(
      Reservation reservation, CursorPageRequest currentRequest) {
    return new CursorPageRequest(
        new KeysetCursor(
            reservation.getReservationDate().atTime(reservation.getReservationTime()),
            reservation.getId(),
            currentRequest.scope()),
        currentRequest.size(),
        currentRequest.scope());
  }

  private record RepairedPage(List<Reservation> reservations, boolean hasNext) {}

  private static LoadReservationExecutionResumePort emptyResumePort() {
    return new LoadReservationExecutionResumePort() {
      @Override
      public java.util.Optional<
              momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                  .ReservationExecutionResumeView>
          loadLatest(Long reservationId) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Map<
              Long,
              momzzangseven.mztkbe.modules.marketplace.reservation.application.dto
                  .ReservationExecutionResumeView>
          loadLatestBatch(java.util.Collection<Long> reservationIds) {
        return java.util.Map.of();
      }
    };
  }

  private static RepairReservationChainReadUseCase noOpRepairUseCase() {
    return new RepairReservationChainReadUseCase() {
      @Override
      public Reservation repairOne(Reservation reservation) {
        return reservation;
      }

      @Override
      public java.util.List<Reservation> repairBatch(java.util.List<Reservation> reservations) {
        return reservations;
      }
    };
  }
}
