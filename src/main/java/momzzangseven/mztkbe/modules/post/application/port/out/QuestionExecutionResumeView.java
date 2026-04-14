package momzzangseven.mztkbe.modules.post.application.port.out;

import java.time.LocalDateTime;

/**
 * Read-side execution summary attached to question detail responses.
 *
 * <p>The shape intentionally excludes sign-request material because public detail reads only expose
 * resumable status, not owner-bound signing payloads.
 */
public record QuestionExecutionResumeView(
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
