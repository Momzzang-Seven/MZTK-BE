package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionResumeView;

public record QuestionWeb3ExecutionResponse(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    Transaction transaction) {

  public static QuestionWeb3ExecutionResponse from(QuestionExecutionResumeView view) {
    if (view == null) {
      return null;
    }
    return new QuestionWeb3ExecutionResponse(
        new Resource(view.resource().type(), view.resource().id(), view.resource().status()),
        view.actionType(),
        new ExecutionIntent(
            view.executionIntent().id(),
            view.executionIntent().status(),
            view.executionIntent().expiresAt()),
        new Execution(view.execution().mode(), view.execution().signCount()),
        view.transaction() == null
            ? null
            : new Transaction(
                view.transaction().id(), view.transaction().status(), view.transaction().txHash()));
  }

  public record Resource(String type, String id, String status) {}

  public record ExecutionIntent(String id, String status, java.time.LocalDateTime expiresAt) {}

  public record Execution(String mode, int signCount) {}

  public record Transaction(Long id, String status, String txHash) {}
}
