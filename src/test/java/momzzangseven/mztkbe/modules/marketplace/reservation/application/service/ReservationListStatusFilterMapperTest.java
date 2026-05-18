package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
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
                ReservationListStatusFilter.PURCHASE_PENDING))
        .isEqualTo(ReservationStatus.PURCHASE_PENDING);
    assertThat(
            ReservationListStatusFilterMapper.toStoredStatus(
                ReservationListStatusFilter.DEADLINE_REFUND_AVAILABLE))
        .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
  }

  @Test
  @DisplayName("raw HOLDING is not a public list filter")
  void holdingIsNotPublicFilter() {
    assertThat(ReservationListStatusFilter.values())
        .extracting(Enum::name)
        .doesNotContain("HOLDING");
  }
}
