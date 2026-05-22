package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverExpiredMarketplaceAdminExecutionAttemptUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

@Slf4j
@RequiredArgsConstructor
public class RecoverExpiredMarketplaceAdminExecutionAttemptService
    implements RecoverExpiredMarketplaceAdminExecutionAttemptUseCase {

  private static final String ERROR_CODE =
      MarketplaceAdminReviewValidationCode.ADMIN_PREPARATION_EXPIRED.name();

  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final SaveReservationEscrowPort saveReservationEscrowPort;
  private final RunReservationTransactionPort transactionPort;

  @Override
  public RecoverExpiredMarketplaceAdminExecutionAttemptResult execute(
      RecoverExpiredMarketplaceAdminExecutionAttemptCommand command) {
    command.validate();
    return transactionPort.requiresNew(() -> recoverLocked(command.now(), command.batchSize()));
  }

  private RecoverExpiredMarketplaceAdminExecutionAttemptResult recoverLocked(
      LocalDateTime now, int batchSize) {
    List<MarketplaceReservationActionState> candidates =
        loadReservationActionStatePort.findExpiredAdminPreparingAttemptsWithLock(now, batchSize);
    int recovered = 0;
    int skipped = 0;
    int failed = 0;
    for (MarketplaceReservationActionState actionState : candidates) {
      try {
        if (recoverOne(actionState)) {
          recovered++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn(
            "Failed to recover expired marketplace admin attempt: actionStateId={}",
            actionState.getId(),
            e);
      }
    }
    return new RecoverExpiredMarketplaceAdminExecutionAttemptResult(
        candidates.size(), recovered, skipped, failed);
  }

  private boolean recoverOne(MarketplaceReservationActionState actionState) {
    if (!isExpiredUnboundAdminPreparing(actionState)) {
      return false;
    }
    Reservation reservation =
        loadReservationPort.findByIdWithLock(actionState.getReservationId()).orElse(null);
    if (reservation == null || !matchesPendingReservation(actionState, reservation)) {
      markStale(actionState, "expired admin attempt no longer matches reservation state");
      return false;
    }
    Reservation restored = saveReservationPort.save(reservation.rollbackToPriorState());
    ReservationEscrowStatus restoredEscrowStatus =
        actionState.getPriorEscrowStatus() == null
            ? restored.getEffectiveEscrowStatus()
            : actionState.getPriorEscrowStatus();
    loadReservationEscrowPort
        .findByReservationIdWithLock(restored.getId())
        .ifPresent(
            escrow ->
                saveReservationEscrowPort.save(
                    escrow.toBuilder()
                        .escrowStatus(restoredEscrowStatus)
                        .lastFailureCode(ERROR_CODE)
                        .lastFailureMessage("expired unbound marketplace admin preparation")
                        .build()));
    saveReservationActionStatePort.save(
        actionState.toBuilder()
            .status(ReservationActionStateStatus.PREPARATION_FAILED)
            .retryable(true)
            .errorCode(ERROR_CODE)
            .errorReason("expired unbound marketplace admin preparation")
            .build());
    return true;
  }

  private static boolean isExpiredUnboundAdminPreparing(
      MarketplaceReservationActionState actionState) {
    return actionState.getStatus() == ReservationActionStateStatus.PREPARING
        && actionState.getExecutionIntentPublicId() == null
        && (actionState.getActionType() == ReservationEscrowAction.ADMIN_REFUND
            || actionState.getActionType() == ReservationEscrowAction.ADMIN_SETTLE);
  }

  private static boolean matchesPendingReservation(
      MarketplaceReservationActionState actionState, Reservation reservation) {
    ReservationStatus expectedStatus =
        actionState.getActionType() == ReservationEscrowAction.ADMIN_REFUND
            ? ReservationStatus.ADMIN_REFUND_PENDING
            : ReservationStatus.ADMIN_SETTLE_PENDING;
    ReservationEscrowStatus expectedEscrowStatus =
        actionState.getActionType() == ReservationEscrowAction.ADMIN_REFUND
            ? ReservationEscrowStatus.ADMIN_REFUND_PENDING
            : ReservationEscrowStatus.ADMIN_SETTLE_PENDING;
    return reservation.getStatus() == expectedStatus
        && reservation.getEffectiveEscrowStatus() == expectedEscrowStatus
        && reservation.getCurrentExecutionIntentPublicId() == null
        && actionState.getAttemptToken() != null
        && actionState.getAttemptToken().equals(reservation.getPendingAttemptToken());
  }

  private void markStale(MarketplaceReservationActionState actionState, String reason) {
    saveReservationActionStatePort.save(
        actionState.toBuilder()
            .status(ReservationActionStateStatus.STALE)
            .retryable(false)
            .errorCode("STALE_ADMIN_PREPARATION")
            .errorReason(reason)
            .build());
  }
}
