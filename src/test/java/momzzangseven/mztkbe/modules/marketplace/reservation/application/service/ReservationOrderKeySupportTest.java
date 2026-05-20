package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationOrderKeySupportTest {

  @Test
  @DisplayName("UUID orderId를 canonical lowercase 0x + 64 hex bytes32 orderKey로 변환한다")
  void fromOrderId_ReturnsCanonicalBytes32Hex() {
    String orderKey =
        ReservationOrderKeySupport.fromOrderId("00000000-0000-0000-0000-000000000001");

    assertThat(orderKey)
        .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000001");
    assertThat(orderKey).hasSize(66);
  }

  @Test
  @DisplayName("저장된 orderKey가 있으면 orderId에서 재계산하지 않고 그대로 사용한다")
  void requireOrderKey_UsesPersistedOrderKeyFirst() {
    Reservation reservation =
        Reservation.builder()
            .orderId("not-a-uuid")
            .orderKey("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .build();

    assertThat(ReservationOrderKeySupport.requireOrderKey(reservation))
        .isEqualTo("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  @Test
  @DisplayName("저장된 orderKey가 없고 orderId도 UUID가 아니면 sync-required 오류를 반환한다")
  void requireOrderKey_InvalidOrderIdThrowsBusinessError() {
    Reservation reservation = Reservation.builder().orderId("not-a-uuid").build();

    assertThatThrownBy(() -> ReservationOrderKeySupport.requireOrderKey(reservation))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED.getCode()));
  }
}
