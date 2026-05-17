package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

final class ReservationDeadlineActionGuard {

  private ReservationDeadlineActionGuard() {}

  static void requireSettlementBeforeContractDeadline(
      Reservation reservation, Clock clock, String action) {
    if (!reservation.getEffectiveEscrowFlow().isUserEip7702()) {
      return;
    }
    if (reservation.getContractDeadlineAt() != null
        && LocalDateTime.now(clock).isAfter(reservation.getContractDeadlineAt())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED,
          "Contract deadline expired; marketplace " + action + " is no longer allowed");
    }
  }
}
