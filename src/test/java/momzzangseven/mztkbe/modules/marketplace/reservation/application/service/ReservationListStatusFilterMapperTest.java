package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationListStatusFilterMapperTest {

  @Test
  @DisplayName("public filters map to the current stored status during Phase 1")
  void publicFiltersMapToStoredStatus() {
    assertThat(ReservationListStatusFilterMapper.toStoredStatus(null)).isNull();
    assertThat(
            ReservationListStatusFilterMapper.toStoredStatus(ReservationListStatusFilter.PENDING))
        .isEqualTo(ReservationStatus.PENDING);
    assertThat(
            ReservationListStatusFilterMapper.toStoredStatus(
                ReservationListStatusFilter.PURCHASE_PREPARING))
        .isEqualTo(ReservationStatus.HOLDING);
    assertThat(
            ReservationListStatusFilterMapper.toStoredStatus(
                ReservationListStatusFilter.PURCHASE_PENDING))
        .isEqualTo(ReservationStatus.HOLDING);
    assertThat(
            ReservationListStatusFilterMapper.toStoredStatus(
                ReservationListStatusFilter.DEADLINE_REFUND_AVAILABLE))
        .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
  }

  @Test
  @DisplayName("purchase public filters are matched against display status, not raw HOLDING status")
  void purchaseFiltersMatchDisplayStatus() {
    Reservation preparing =
        Reservation.builder()
            .id(1L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
            .build();
    Reservation pending =
        Reservation.builder()
            .id(2L)
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
            .currentExecutionIntentPublicId("intent-1")
            .build();

    assertThat(
            ReservationListStatusFilterMapper.matchesDisplayStatus(
                pending, ReservationListStatusFilter.PURCHASE_PENDING))
        .isTrue();
    assertThat(
            ReservationListStatusFilterMapper.matchesDisplayStatus(
                preparing, ReservationListStatusFilter.PURCHASE_PENDING))
        .isFalse();
    assertThat(
            ReservationListStatusFilterMapper.matchesDisplayStatus(
                preparing, ReservationListStatusFilter.PURCHASE_PREPARING))
        .isTrue();
  }

  @Test
  @DisplayName("raw HOLDING is not a public list filter")
  void holdingIsNotPublicFilter() {
    assertThat(ReservationListStatusFilter.values())
        .extracting(Enum::name)
        .doesNotContain("HOLDING");
  }
}
