package momzzangseven.mztkbe.modules.post.application.port.out;

public record QuestionPublicationEvidence(
    boolean lifecycleManaged,
    boolean projectionExists,
    String projectionState,
    boolean activeCreateIntentExists,
    boolean terminalCreateIntentExists,
    String latestCreateIntentStatus,
    String latestCreateExecutionIntentId) {

  public QuestionPublicationEvidence(
      boolean lifecycleManaged,
      boolean projectionExists,
      boolean activeCreateIntentExists,
      boolean terminalCreateIntentExists,
      String latestCreateIntentStatus) {
    this(
        lifecycleManaged,
        projectionExists,
        projectionExists ? "CREATED" : null,
        activeCreateIntentExists,
        terminalCreateIntentExists,
        latestCreateIntentStatus,
        null);
  }

  public static QuestionPublicationEvidence unmanaged() {
    return new QuestionPublicationEvidence(false, false, null, false, false, null, null);
  }

  public boolean projectionSupportsPublication() {
    return projectionExists && !projectionDeleted();
  }

  public boolean projectionDeleted() {
    return "DELETED".equals(projectionState) || "DELETED_WITH_ANSWERS".equals(projectionState);
  }

  public boolean hasLatestCreateExecutionIntent(String executionIntentId) {
    return executionIntentId != null
        && !executionIntentId.isBlank()
        && executionIntentId.equals(latestCreateExecutionIntentId);
  }
}
