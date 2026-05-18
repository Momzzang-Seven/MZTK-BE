package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RepairReservationChainReadUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

/**
 * Returns the full detail of a single reservation.
 *
 * <p>Access is granted to both the owning user and the associated trainer. Any other requester
 * receives a {@link MarketplaceUnauthorizedAccessException}.
 *
 * <p>Enrichment strategy:
 *
 * <ul>
 *   <li>{@code classTitle} and {@code priceAmount} are read from the denormalised snapshot fields
 *       on the {@link Reservation} domain object (set at booking time), so they remain accurate
 *       even if the trainer later renames the class or changes the price.
 *   <li>{@code thumbnailFinalObjectKey} is still resolved live from the {@code classes} module (no
 *       snapshot exists). If the class is inactive, it may be null.
 *   <li>Trainer and user nicknames are resolved live from the {@code user} module.
 * </ul>
 */
public class GetReservationDetailService implements GetReservationDetailUseCase {

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;
  private final LoadReservationExecutionResumePort loadReservationExecutionResumePort;
  private final RepairReservationChainReadUseCase repairReservationChainReadUseCase;
  private final LoadReservationEscrowPort loadReservationEscrowPort;

  public GetReservationDetailService(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase,
      LoadReservationEscrowPort loadReservationEscrowPort) {
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
    this.loadReservationEscrowPort = loadReservationEscrowPort;
  }

  public GetReservationDetailService(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort,
      LoadReservationExecutionResumePort loadReservationExecutionResumePort,
      RepairReservationChainReadUseCase repairReservationChainReadUseCase) {
    this(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        loadReservationExecutionResumePort,
        repairReservationChainReadUseCase,
        null);
  }

  public GetReservationDetailService(
      LoadReservationPort loadReservationPort,
      LoadClassSummaryPort loadClassSummaryPort,
      LoadUserSummaryPort loadUserSummaryPort) {
    this(
        loadReservationPort,
        loadClassSummaryPort,
        loadUserSummaryPort,
        emptyResumePort(),
        noOpRepairUseCase(),
        null);
  }

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

  @Override
  public GetReservationResult execute(GetReservationQuery query) {
    query.validate();

    Reservation reservation =
        loadReservationPort
            .findById(query.reservationId())
            .orElseThrow(() -> new ReservationNotFoundException(query.reservationId()));

    Long requesterId = query.requesterId();
    if (!reservation.isOwnedByUser(requesterId) && !reservation.isOwnedByTrainer(requesterId)) {
      throw new MarketplaceUnauthorizedAccessException();
    }
    reservation = repairReservationChainReadUseCase.repairOne(reservation);
    reservation = applyEscrowTxHashOverride(reservation);

    // Snapshot fields take precedence for price and title (immutable at booking time).
    // Both snapshot fields must be present — if only one is populated (partial snapshot)
    // we fall back to a live cross-module lookup to avoid returning null classTitle or priceAmount.
    String classTitle;
    Integer priceAmount;
    String thumbnailFinalObjectKey;

    if (reservation.getBookedPriceAmount() != null && reservation.getBookedClassTitle() != null) {
      // New record: use snapshot values; still resolve thumbnail live (not snapshotted).
      classTitle = reservation.getBookedClassTitle();
      priceAmount = reservation.getBookedPriceAmount();
      thumbnailFinalObjectKey =
          loadClassSummaryPort
              .findBySlotId(reservation.getSlotId())
              .map(ClassSummary::thumbnailFinalObjectKey)
              .orElse(null);
    } else {
      // Legacy record (pre-snapshot migration) or partial snapshot: fall back to full
      // cross-module lookup so neither field is returned as null.
      ClassSummary classSummary =
          loadClassSummaryPort.findBySlotId(reservation.getSlotId()).orElse(null);
      classTitle = classSummary != null ? classSummary.title() : null;
      priceAmount = classSummary != null ? classSummary.priceAmount() : null;
      thumbnailFinalObjectKey =
          classSummary != null ? classSummary.thumbnailFinalObjectKey() : null;
    }

    UserSummary trainerSummary =
        loadUserSummaryPort.findById(reservation.getTrainerId()).orElse(null);
    UserSummary userSummary = loadUserSummaryPort.findById(reservation.getUserId()).orElse(null);

    return ReservationDisplayStatusMapper.detailResult(
        reservation,
        classTitle,
        priceAmount,
        thumbnailFinalObjectKey,
        trainerSummary != null ? trainerSummary.nickname() : null,
        userSummary != null ? userSummary.nickname() : null,
        requesterId,
        ReservationExecutionResumeViewer.hydrate(
            reservation,
            requesterId,
            loadReservationExecutionResumePort.loadLatest(reservation.getId()).orElse(null)));
  }

  private Reservation applyEscrowTxHashOverride(Reservation reservation) {
    if (loadReservationEscrowPort == null) {
      return reservation;
    }
    return loadReservationEscrowPort
        .findByReservationId(reservation.getId())
        .map(MarketplaceReservationEscrow::getLastTxHash)
        .filter(txHash -> txHash != null && !txHash.isBlank())
        .map(txHash -> reservation.toBuilder().txHash(txHash).build())
        .orElse(reservation);
  }
}
