package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationDisplayStatusMapperTest {

  @Test
  @DisplayName("stored HOLDING is displayed as purchase preparing and hides business status")
  void holdingMapsToPurchasePreparing() {
    Reservation reservation =
        Reservation.builder()
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.NONE)
            .build();

    assertThat(ReservationDisplayStatusMapper.displayStatus(reservation))
        .isEqualTo(ReservationDisplayStatus.PURCHASE_PREPARING);
    assertThat(ReservationDisplayStatusMapper.businessStatus(reservation)).isNull();
  }

  @Test
  @DisplayName("stored HOLDING with bound intent is displayed as purchase pending")
  void holdingWithIntentMapsToPurchasePending() {
    Reservation reservation =
        Reservation.builder()
            .status(ReservationStatus.HOLDING)
            .escrowStatus(ReservationEscrowStatus.NONE)
            .currentExecutionIntentPublicId("intent-1")
            .build();

    assertThat(ReservationDisplayStatusMapper.displayStatus(reservation))
        .isEqualTo(ReservationDisplayStatus.PURCHASE_PENDING);
    assertThat(ReservationDisplayStatusMapper.businessStatus(reservation)).isNull();
  }

  @Test
  @DisplayName("normal stored status is exposed as matching display status and business status")
  void normalStatusMapsDirectly() {
    Reservation reservation = Reservation.builder().status(ReservationStatus.APPROVED).build();

    assertThat(ReservationDisplayStatusMapper.displayStatus(reservation))
        .isEqualTo(ReservationDisplayStatus.APPROVED);
    assertThat(ReservationDisplayStatusMapper.businessStatus(reservation))
        .isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  @DisplayName("admin pending statuses are public display states with hidden business status")
  void adminPendingMapsToDisplayAndHidesBusinessStatus() {
    Reservation refund =
        Reservation.builder()
            .status(ReservationStatus.ADMIN_REFUND_PENDING)
            .escrowStatus(ReservationEscrowStatus.ADMIN_REFUND_PENDING)
            .build();
    Reservation settle =
        Reservation.builder()
            .status(ReservationStatus.ADMIN_SETTLE_PENDING)
            .escrowStatus(ReservationEscrowStatus.ADMIN_SETTLE_PENDING)
            .build();

    assertThat(ReservationDisplayStatusMapper.displayStatus(refund))
        .isEqualTo(ReservationDisplayStatus.ADMIN_REFUND_PENDING);
    assertThat(ReservationDisplayStatusMapper.businessStatus(refund)).isNull();
    assertThat(ReservationDisplayStatusMapper.displayStatus(settle))
        .isEqualTo(ReservationDisplayStatus.ADMIN_SETTLE_PENDING);
    assertThat(ReservationDisplayStatusMapper.businessStatus(settle)).isNull();
  }
}
