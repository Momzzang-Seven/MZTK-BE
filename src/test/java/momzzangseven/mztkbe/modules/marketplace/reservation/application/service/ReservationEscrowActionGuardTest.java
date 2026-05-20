package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationEscrowActionGuardTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Test
  @DisplayName("USER_EIP7702 예약은 LOCKED escrow에서만 user action을 허용한다")
  void requireUserEscrowLocked_RequiresLockedUserEscrow() {
    Reservation locked =
        reservation()
            .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
            .escrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    Reservation notLocked =
        locked.toBuilder().escrowStatus(ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED).build();

    assertThatCode(() -> ReservationEscrowActionGuard.requireUserEscrowLocked(locked, "cancel"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> ReservationEscrowActionGuard.requireUserEscrowLocked(notLocked, "cancel"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
  }

  @Test
  @DisplayName("active wallet이 예약 wallet snapshot과 다르면 switch-wallet 오류를 반환한다")
  void requireActiveWalletMatchesSnapshot_RejectsMismatch() {
    LoadReservationWalletPort walletPort =
        userId -> Optional.of("0x1111111111111111111111111111111111111111");

    assertThatThrownBy(
            () ->
                ReservationEscrowActionGuard.requireActiveWalletMatchesSnapshot(
                    walletPort, 1L, "0x2222222222222222222222222222222222222222"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED.getCode()));
  }

  @Test
  @DisplayName("contract deadline 이후 settlement action은 deadline refund 필요 오류로 차단한다")
  void requireSettlementBeforeContractDeadline_BlocksAfterDeadline() {
    Reservation reservation =
        reservation()
            .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
            .escrowStatus(ReservationEscrowStatus.LOCKED)
            .contractDeadlineAt(LocalDateTime.now(CLOCK).minusSeconds(1))
            .build();

    assertThatThrownBy(
            () ->
                ReservationEscrowActionGuard.requireSettlementBeforeContractDeadline(
                    reservation, CLOCK, "complete"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED.getCode()));
  }

  private Reservation.ReservationBuilder reservation() {
    return Reservation.builder().id(1L).userId(10L).trainerId(20L);
  }
}
