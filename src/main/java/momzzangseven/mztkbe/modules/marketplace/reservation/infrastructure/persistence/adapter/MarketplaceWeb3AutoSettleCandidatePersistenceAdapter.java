package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleCandidate;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.FindMarketplaceWeb3AutoSettleCandidatesPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarketplaceWeb3AutoSettleCandidatePersistenceAdapter
    implements FindMarketplaceWeb3AutoSettleCandidatesPort {

  private final ReservationJpaRepository reservationJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public List<MarketplaceWeb3AutoSettleCandidate> findCandidates(
      LocalDateTime now,
      LocalDateTime settleCutoff,
      MarketplaceWeb3AutoSettleScanCursor cursor,
      int scanSize) {
    return reservationJpaRepository
        .findMarketplaceWeb3AutoSettleCandidates(
            now,
            settleCutoff.toLocalDate(),
            settleCutoff.toLocalTime(),
            cursor.reservationDate(),
            cursor.reservationTime(),
            cursor.reservationId(),
            scanSize)
        .stream()
        .map(this::toCandidate)
        .toList();
  }

  private MarketplaceWeb3AutoSettleCandidate toCandidate(
      ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection row) {
    return new MarketplaceWeb3AutoSettleCandidate(
        row.getReservationId(),
        row.getOrderKey(),
        row.getReservationDate(),
        row.getReservationTime(),
        row.getDurationMinutes(),
        row.getContractDeadlineAt());
  }
}
