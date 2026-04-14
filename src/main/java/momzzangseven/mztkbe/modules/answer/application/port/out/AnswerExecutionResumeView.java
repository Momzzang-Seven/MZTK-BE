package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.time.LocalDateTime;

/**
 * Read-side execution summary attached to owner-visible answer rows.
 *
 * <p>Only the answer owner receives this summary in API reads, so the contract carries status and
 * transaction pointers without exposing sign-request material.
 */
public record AnswerExecutionResumeView(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    Transaction transaction) {

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {}

  public record Execution(String mode, int signCount) {}

  public record Transaction(Long id, String status, String txHash) {}
}
