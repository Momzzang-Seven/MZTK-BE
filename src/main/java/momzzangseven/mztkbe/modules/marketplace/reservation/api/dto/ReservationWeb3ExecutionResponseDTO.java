package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;

public record ReservationWeb3ExecutionResponseDTO(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    Transaction transaction,
    String viewerAction,
    boolean viewerCanExecute,
    boolean viewerCanRecover) {

  public static ReservationWeb3ExecutionResponseDTO from(ReservationExecutionResumeView view) {
    if (view == null) {
      return null;
    }
    return new ReservationWeb3ExecutionResponseDTO(
        new Resource(view.resource().type(), view.resource().id(), view.resource().status()),
        view.actionType(),
        new ExecutionIntent(
            view.executionIntent().id(),
            view.executionIntent().status(),
            view.executionIntent().expiresAt(),
            view.executionIntent().expiresAtEpochSeconds()),
        new Execution(view.execution().mode(), view.execution().signCount()),
        view.transaction() == null
            ? null
            : new Transaction(
                view.transaction().id(), view.transaction().status(), view.transaction().txHash()),
        view.viewerAction(),
        view.viewerCanExecute(),
        view.viewerCanRecover());
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record Transaction(Long id, String status, String txHash) {}
}
