package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;

public record ReservationExecutionResumeView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    Transaction transaction,
    String viewerAction,
    boolean viewerCanExecute,
    boolean viewerCanRecover) {

  public ReservationExecutionResumeView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      Transaction transaction) {
    this(resource, actionType, executionIntent, execution, transaction, null, false, false);
  }

  public ReservationExecutionResumeView withViewer(
      String viewerAction, boolean viewerCanExecute, boolean viewerCanRecover) {
    return new ReservationExecutionResumeView(
        resource,
        actionType,
        executionIntent,
        execution,
        transaction,
        viewerAction,
        viewerCanExecute,
        viewerCanRecover);
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record Transaction(Long id, String status, String txHash) {}
}
