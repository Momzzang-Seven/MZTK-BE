package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter.MarketplaceWeb3AutoSettleCandidatePersistenceAdapter;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceWeb3AutoSettleCandidatePersistenceAdapterTest {

  @Mock private ReservationJpaRepository reservationJpaRepository;
  @Mock private ReservationJpaRepository.MarketplaceWeb3AutoSettleCandidateProjection row;

  @Test
  void splitsCutoffIntoDateTimeAndMapsProjection() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0);
    LocalDateTime settleCutoff = LocalDateTime.of(2026, 5, 28, 12, 0);
    MarketplaceWeb3AutoSettleScanCursor cursor =
        new MarketplaceWeb3AutoSettleScanCursor(LocalDate.of(2026, 5, 28), LocalTime.of(9, 0), 77L);
    given(row.getReservationId()).willReturn(10L);
    given(row.getOrderKey()).willReturn("0xorder");
    given(row.getReservationDate()).willReturn(LocalDate.of(2026, 5, 27));
    given(row.getReservationTime()).willReturn(LocalTime.of(10, 30));
    given(row.getDurationMinutes()).willReturn(60);
    given(row.getContractDeadlineAt()).willReturn(LocalDateTime.of(2026, 5, 30, 0, 0));
    given(
            reservationJpaRepository.findMarketplaceWeb3AutoSettleCandidates(
                now,
                settleCutoff.toLocalDate(),
                settleCutoff.toLocalTime(),
                cursor.reservationDate(),
                cursor.reservationTime(),
                cursor.reservationId(),
                25))
        .willReturn(List.of(row));

    var result =
        new MarketplaceWeb3AutoSettleCandidatePersistenceAdapter(reservationJpaRepository)
            .findCandidates(now, settleCutoff, cursor, 25);

    assertThat(result)
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.reservationId()).isEqualTo(10L);
              assertThat(candidate.orderKey()).isEqualTo("0xorder");
              assertThat(candidate.reservationDate()).isEqualTo(LocalDate.of(2026, 5, 27));
              assertThat(candidate.reservationTime()).isEqualTo(LocalTime.of(10, 30));
              assertThat(candidate.durationMinutes()).isEqualTo(60);
              assertThat(candidate.contractDeadlineAt())
                  .isEqualTo(LocalDateTime.of(2026, 5, 30, 0, 0));
            });
  }
}
