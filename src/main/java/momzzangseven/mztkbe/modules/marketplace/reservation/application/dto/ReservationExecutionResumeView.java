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
    boolean viewerCanRecover,
    String recoveryStatus,
    String recoveryReason,
    Boolean retryAllowed) {

  public ReservationExecutionResumeView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      Transaction transaction) {
    this(
        resource,
        actionType,
        executionIntent,
        execution,
        transaction,
        null,
        false,
        false,
        recoveryStatus(transaction),
        recoveryReason(transaction),
        retryAllowed(transaction));
  }

  public ReservationExecutionResumeView(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      Transaction transaction,
      String viewerAction,
      boolean viewerCanExecute,
      boolean viewerCanRecover) {
    this(
        resource,
        actionType,
        executionIntent,
        execution,
        transaction,
        viewerAction,
        viewerCanExecute,
        viewerCanRecover,
        recoveryStatus(transaction),
        recoveryReason(transaction),
        retryAllowed(transaction));
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
        viewerCanRecover,
        recoveryStatus,
        recoveryReason,
        retryAllowed);
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(
      String id, String status, LocalDateTime expiresAt, long expiresAtEpochSeconds) {}

  public record Execution(String mode, int signCount) {}

  public record Transaction(Long id, String status, String txHash) {}

  private static String recoveryStatus(Transaction transaction) {
    return isUnconfirmed(transaction) ? "ONCHAIN_UNCERTAIN" : null;
  }

  private static String recoveryReason(Transaction transaction) {
    return isUnconfirmed(transaction) ? "RECEIPT_TIMEOUT" : null;
  }

  private static Boolean retryAllowed(Transaction transaction) {
    return isUnconfirmed(transaction) ? Boolean.FALSE : null;
  }

  private static boolean isUnconfirmed(Transaction transaction) {
    return transaction != null && "UNCONFIRMED".equals(transaction.status());
  }
}
