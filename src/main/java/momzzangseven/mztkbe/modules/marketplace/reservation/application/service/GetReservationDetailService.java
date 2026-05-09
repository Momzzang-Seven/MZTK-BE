package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Service
@RequiredArgsConstructor
public class GetReservationDetailService implements GetReservationDetailUseCase {

  private final LoadReservationPort loadReservationPort;
  private final LoadClassSummaryPort loadClassSummaryPort;
  private final LoadUserSummaryPort loadUserSummaryPort;

  @Override
  @Transactional(readOnly = true)
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

    return GetReservationResult.from(
        reservation,
        classTitle,
        priceAmount,
        thumbnailFinalObjectKey,
        trainerSummary != null ? trainerSummary.nickname() : null,
        userSummary != null ? userSummary.nickname() : null);
  }
}
