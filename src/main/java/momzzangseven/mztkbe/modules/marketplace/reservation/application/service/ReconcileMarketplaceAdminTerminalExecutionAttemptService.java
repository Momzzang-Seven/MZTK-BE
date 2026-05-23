package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayTerminatedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;

@Slf4j
@RequiredArgsConstructor
public class ReconcileMarketplaceAdminTerminalExecutionAttemptService
    implements ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase {

  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final ReplayTerminatedReservationExecutionPort replayTerminatedReservationExecutionPort;

  @Override
  public ReconcileMarketplaceAdminTerminalExecutionAttemptResult execute(
      ReconcileMarketplaceAdminTerminalExecutionAttemptCommand command) {
    command.validate();
    var candidates =
        loadReservationActionStatePort.findBoundAdminExecutionAttemptsForTerminalReplay(
            command.batchSize());
    int replayed = 0;
    int skipped = 0;
    int failed = 0;
    for (MarketplaceReservationActionState actionState : candidates) {
      String expectedActionType = expectedExecutionActionType(actionState.getActionType());
      String executionIntentId = actionState.getExecutionIntentPublicId();
      if (expectedActionType == null || executionIntentId == null || executionIntentId.isBlank()) {
        skipped++;
        continue;
      }
      try {
        if (replayTerminatedReservationExecutionPort.replayTerminated(
            executionIntentId, expectedActionType)) {
          replayed++;
        } else {
          skipped++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn(
            "Failed to replay marketplace admin terminal hook: actionStateId={}, executionIntentId={}",
            actionState.getId(),
            executionIntentId,
            e);
      }
    }
    return new ReconcileMarketplaceAdminTerminalExecutionAttemptResult(
        candidates.size(), replayed, skipped, failed);
  }

  private String expectedExecutionActionType(ReservationEscrowAction actionType) {
    return switch (actionType) {
      case ADMIN_REFUND -> "MARKETPLACE_ADMIN_REFUND";
      case ADMIN_SETTLE -> "MARKETPLACE_ADMIN_SETTLE";
      default -> null;
    };
  }
}
