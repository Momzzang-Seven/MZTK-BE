package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;

/** Shared guards for user-managed marketplace escrow actions. */
final class ReservationEscrowActionGuard {

  private ReservationEscrowActionGuard() {}

  static void requireUserEscrowLocked(Reservation reservation, String action) {
    if (!reservation.getEffectiveEscrowFlow().isUserEip7702()
        || reservation.getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot "
              + action
              + " reservation before marketplace user escrow is confirmed and locked");
    }
  }

  static void requireActiveWalletMatchesSnapshot(
      LoadReservationWalletPort walletPort, Long userId, String expectedWalletAddress) {
    if (expectedWalletAddress == null || expectedWalletAddress.isBlank()) {
      return;
    }
    String activeWallet = activeWalletOrThrow(walletPort, userId);
    if (!expectedWalletAddress.equalsIgnoreCase(activeWallet)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED,
          "active wallet does not match reservation wallet snapshot");
    }
  }

  static String walletOrSnapshot(
      LoadReservationWalletPort walletPort, String snapshot, Long userId) {
    if (snapshot != null && !snapshot.isBlank()) {
      return snapshot;
    }
    return activeWalletOrThrow(walletPort, userId);
  }

  static void requireSettlementBeforeContractDeadline(
      Reservation reservation, Clock clock, String action) {
    if (!reservation.getEffectiveEscrowFlow().isUserEip7702()) {
      return;
    }
    if (isAfterContractDeadline(reservation, clock)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED,
          "Contract deadline expired; marketplace " + action + " is no longer allowed");
    }
  }

  static boolean isAfterContractDeadline(Reservation reservation, Clock clock) {
    return reservation.getContractDeadlineAt() != null
        && !LocalDateTime.now(clock).isBefore(reservation.getContractDeadlineAt());
  }

  private static String activeWalletOrThrow(LoadReservationWalletPort walletPort, Long userId) {
    return walletPort
        .loadActiveWalletAddress(userId)
        .orElseThrow(() -> new WalletNotConnectedException(userId));
  }
}
