package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns the full detail of a single reservation.
 *
 * <p>Access is granted to both the owning user and the associated trainer. Any other requester
 * receives a {@link MarketplaceUnauthorizedAccessException}.
 */
@Service
@RequiredArgsConstructor
public class GetReservationDetailService implements GetReservationDetailUseCase {

  private final LoadReservationPort loadReservationPort;

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

    return GetReservationResult.from(reservation);
  }
}
