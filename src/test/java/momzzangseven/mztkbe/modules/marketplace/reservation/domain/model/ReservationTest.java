package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationTest {

  private Reservation createDefaultPendingReservation() {
    return Reservation.createPending(
        1L,
        2L,
        3L,
        LocalDate.of(2025, 1, 1),
        LocalTime.of(10, 0),
        60,
        "Request note",
        "order-123",
        "tx-123",
        50000,
        "요가 기초");
  }

  @Test
  @DisplayName("createPending - successfully creates a pending reservation")
  void createPending_Success() {
    Reservation reservation = createDefaultPendingReservation();

    assertThat(reservation.getUserId()).isEqualTo(1L);
    assertThat(reservation.getTrainerId()).isEqualTo(2L);
    assertThat(reservation.getSlotId()).isEqualTo(3L);
    assertThat(reservation.getReservationDate()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(reservation.getReservationTime()).isEqualTo(LocalTime.of(10, 0));
    assertThat(reservation.getDurationMinutes()).isEqualTo(60);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(reservation.getUserRequest()).isEqualTo("Request note");
    assertThat(reservation.getOrderId()).isEqualTo("order-123");
    assertThat(reservation.getTxHash()).isEqualTo("tx-123");
  }

  @Test
  @DisplayName("approve - transitions PENDING to APPROVED")
  void approve_Success() {
    Reservation pending = createDefaultPendingReservation();
    Reservation approved = pending.approve();

    assertThat(approved.getStatus()).isEqualTo(ReservationStatus.APPROVED);
    assertThat(approved.getTxHash()).isEqualTo("tx-123"); // txHash is preserved
  }

  @Test
  @DisplayName("approve - throws if not PENDING")
  void approve_ThrowsIfNotPending() {
    Reservation approved = createDefaultPendingReservation().approve();

    assertThatThrownBy(approved::approve)
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("Cannot transition from APPROVED to APPROVED");
  }

  @Test
  @DisplayName("cancelByUser - transitions PENDING to USER_CANCELLED")
  void cancelByUser_Success() {
    Reservation pending = createDefaultPendingReservation();
    Reservation cancelled = pending.cancelByUser("cancel-tx");

    assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.USER_CANCELLED);
    assertThat(cancelled.getTxHash()).isEqualTo("cancel-tx");
  }

  @Test
  @DisplayName("reject - transitions PENDING to REJECTED")
  void reject_Success() {
    Reservation pending = createDefaultPendingReservation();
    Reservation rejected = pending.reject("reject-tx", "Too busy");

    assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(rejected.getTxHash()).isEqualTo("reject-tx");
  }

  @Test
  @DisplayName("complete - transitions APPROVED to SETTLED")
  void complete_Success() {
    Reservation approved = createDefaultPendingReservation().approve();
    Reservation settled = approved.complete("confirm-tx");

    assertThat(settled.getStatus()).isEqualTo(ReservationStatus.SETTLED);
    assertThat(settled.getTxHash()).isEqualTo("confirm-tx");
  }

  @Test
  @DisplayName("timeoutCancel - transitions PENDING to TIMEOUT_CANCELLED")
  void timeoutCancel_Success() {
    Reservation pending = createDefaultPendingReservation();
    Reservation timeoutCancelled = pending.timeoutCancel("refund-tx");

    assertThat(timeoutCancelled.getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(timeoutCancelled.getTxHash()).isEqualTo("refund-tx");
  }

  @Test
  @DisplayName("autoSettle - transitions APPROVED to AUTO_SETTLED")
  void autoSettle_Success() {
    Reservation approved = createDefaultPendingReservation().approve();
    Reservation autoSettled = approved.autoSettle("admin-settle-tx");

    assertThat(autoSettled.getStatus()).isEqualTo(ReservationStatus.AUTO_SETTLED);
    assertThat(autoSettled.getTxHash()).isEqualTo("admin-settle-tx");
  }

  @Test
  @DisplayName("isOwnedByUser - checks ownership correctly")
  void isOwnedByUser_Correctly() {
    Reservation reservation = createDefaultPendingReservation();

    assertThat(reservation.isOwnedByUser(1L)).isTrue();
    assertThat(reservation.isOwnedByUser(999L)).isFalse();
  }

  @Test
  @DisplayName("isOwnedByTrainer - checks ownership correctly")
  void isOwnedByTrainer_Correctly() {
    Reservation reservation = createDefaultPendingReservation();

    assertThat(reservation.isOwnedByTrainer(2L)).isTrue();
    assertThat(reservation.isOwnedByTrainer(999L)).isFalse();
  }

  @Test
  @DisplayName("sessionEndAt - computes end time correctly")
  void sessionEndAt_ComputesCorrectly() {
    Reservation reservation = createDefaultPendingReservation();
    LocalDateTime endAt = reservation.sessionEndAt();

    // 2025-01-01T10:00 + 60 minutes = 2025-01-01T11:00
    assertThat(endAt).isEqualTo(LocalDateTime.of(2025, 1, 1, 11, 0));
  }

  @Test
  @DisplayName("user escrow purchase transitions stay scheduler-invisible until confirmed")
  void userEscrowPurchaseTransitions() {
    Reservation pending = createDefaultPendingReservation();

    Reservation preparing =
        pending.beginPurchasePreparing(
            "key-hash",
            "payload-hash",
            LocalDateTime.of(2026, 5, 16, 10, 0),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "purchase-token");
    Reservation purchasePending = preparing.bindPurchaseIntent("intent-public-id");
    Reservation retryPreparing =
        purchasePending.retryPurchasePreparing(
            "purchase-token-2", LocalDateTime.of(2026, 5, 16, 10, 5));
    Reservation locked =
        purchasePending.markPurchaseConfirmedLocked(
            1_800_000_000L, LocalDateTime.of(2027, 1, 15, 8, 0));

    assertThat(preparing.getStatus()).isEqualTo(ReservationStatus.HOLDING);
    assertThat(preparing.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.PURCHASE_PREPARING);
    assertThat(preparing.getEscrowFlow()).isEqualTo(ReservationEscrowFlow.USER_EIP7702);
    assertThat(preparing.getPendingAttemptToken()).isEqualTo("purchase-token");
    assertThat(preparing.getStatus().isSchedulerInvisibleUserState()).isTrue();
    assertThat(purchasePending.getStatus()).isEqualTo(ReservationStatus.HOLDING);
    assertThat(purchasePending.getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.PURCHASE_PENDING);
    assertThat(purchasePending.getCurrentExecutionIntentPublicId()).isEqualTo("intent-public-id");
    assertThat(retryPreparing.getStatus()).isEqualTo(ReservationStatus.HOLDING);
    assertThat(retryPreparing.getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.PURCHASE_PREPARING);
    assertThat(retryPreparing.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(retryPreparing.getPendingAttemptToken()).isEqualTo("purchase-token-2");
    assertThat(locked.getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(locked.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(locked.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(locked.getPendingAction()).isNull();
    assertThat(locked.getPendingAttemptToken()).isNull();
    assertThat(locked.getContractDeadlineEpochSeconds()).isEqualTo(1_800_000_000L);
    assertThat(locked.getContractDeadlineAt()).isEqualTo(LocalDateTime.of(2027, 1, 15, 8, 0));
    assertThat(locked.isLegacySchedulerEligibleAt(LocalDateTime.of(2026, 5, 16, 0, 0))).isFalse();
  }

  @Test
  @DisplayName("deadline refund transitions use scheduler-invisible business statuses")
  void deadlineRefundTransitions() {
    Reservation locked =
        createDefaultPendingReservation()
            .beginPurchasePreparing("key", "payload", LocalDateTime.of(2026, 5, 16, 10, 0))
            .bindPurchaseIntent("intent")
            .markPurchaseConfirmedLocked(100L, LocalDateTime.of(2026, 5, 16, 9, 0));

    Reservation available = locked.markDeadlineRefundAvailable();
    Reservation pending = available.beginDeadlineRefundPending("attempt-token");
    Reservation refunded = pending.markDeadlineRefunded("refund-tx");

    assertThat(available.getStatus()).isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
    assertThat(available.getStatus().isSchedulerInvisibleUserState()).isTrue();
    assertThat(pending.getStatus()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    assertThat(pending.getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_REFUND_PENDING);
    assertThat(refunded.getStatus()).isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    assertThat(refunded.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.DEADLINE_REFUNDED);
    assertThat(refunded.getTxHash()).isEqualTo("refund-tx");
    assertThat(refunded.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(refunded.getPendingAction()).isNull();
    assertThat(refunded.getPendingAttemptToken()).isNull();
    assertThat(refunded.getStatus().isTerminal()).isTrue();
  }

  @Test
  @DisplayName("manual sync status allows explicit repair exits")
  void manualSyncRequired_allowsExplicitRepairExits() {
    assertThat(ReservationStatus.MANUAL_SYNC_REQUIRED.canTransitionTo(ReservationStatus.SETTLED))
        .isTrue();
    assertThat(
            ReservationStatus.MANUAL_SYNC_REQUIRED.canTransitionTo(
                ReservationStatus.DEADLINE_REFUND_AVAILABLE))
        .isTrue();
    assertThat(ReservationStatus.MANUAL_SYNC_REQUIRED.canTransitionTo(ReservationStatus.PENDING))
        .isFalse();
  }

  @Test
  @DisplayName("confirmed cancel/reject/confirm transitions close pending execution state")
  void confirmedTerminalTransitions_clearPendingExecutionState() {
    Reservation locked =
        createDefaultPendingReservation()
            .beginPurchasePreparing("key", "payload", LocalDateTime.of(2026, 5, 16, 10, 0))
            .bindPurchaseIntent("purchase-intent")
            .markPurchaseConfirmedLocked(100L, LocalDateTime.of(2026, 5, 16, 9, 0));

    Reservation cancelled =
        locked
            .beginCancelPending("cancel-token")
            .bindPendingExecutionIntent("cancel-intent")
            .cancelByUser("cancel-tx");
    Reservation rejected =
        locked
            .beginRejectPending("reject-token", "일정 불가")
            .bindPendingExecutionIntent("reject-intent")
            .reject("reject-tx", "일정 불가");
    Reservation settled =
        locked
            .approve()
            .beginConfirmPending("confirm-token")
            .bindPendingExecutionIntent("confirm-intent")
            .complete("confirm-tx");

    assertTerminalEscrowClosed(cancelled, ReservationStatus.USER_CANCELLED);
    assertThat(cancelled.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertTerminalEscrowClosed(rejected, ReservationStatus.REJECTED);
    assertThat(rejected.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(rejected.getRejectionReason()).isEqualTo("일정 불가");
    assertTerminalEscrowClosed(settled, ReservationStatus.SETTLED);
    assertThat(settled.getEscrowStatus()).isEqualTo(ReservationEscrowStatus.SETTLED);
  }

  private void assertTerminalEscrowClosed(
      Reservation reservation, ReservationStatus expectedStatus) {
    assertThat(reservation.getStatus()).isEqualTo(expectedStatus);
    assertThat(reservation.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(reservation.getPendingAction()).isNull();
    assertThat(reservation.getPendingAttemptToken()).isNull();
    assertThat(reservation.getPriorStatus()).isNull();
    assertThat(reservation.getPriorEscrowStatus()).isNull();
  }

  @Test
  @DisplayName("legacy scheduler guard rejects USER_EIP7702 and expired deadline rows")
  void legacySchedulerGuard() {
    Reservation legacy = createDefaultPendingReservation();
    Reservation userFlow =
        legacy.beginPurchasePreparing("key", "payload", LocalDateTime.of(2026, 5, 16, 10, 0));
    Reservation expiredLegacy =
        legacy.toBuilder().contractDeadlineAt(LocalDateTime.of(2026, 5, 16, 9, 0)).build();

    assertThat(legacy.isLegacySchedulerEligibleAt(LocalDateTime.of(2026, 5, 16, 8, 0))).isTrue();
    assertThat(userFlow.isLegacySchedulerEligibleAt(LocalDateTime.of(2026, 5, 16, 8, 0))).isFalse();
    assertThat(expiredLegacy.isLegacySchedulerEligibleAt(LocalDateTime.of(2026, 5, 16, 9, 1)))
        .isFalse();
  }
}
