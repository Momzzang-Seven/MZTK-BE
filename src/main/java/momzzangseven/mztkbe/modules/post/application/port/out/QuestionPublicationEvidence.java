package momzzangseven.mztkbe.modules.post.application.port.out;

public record QuestionPublicationEvidence(
    boolean lifecycleManaged,
    boolean projectionExists,
    boolean activeCreateIntentExists,
    boolean terminalCreateIntentExists,
    String latestCreateIntentStatus) {

  public static QuestionPublicationEvidence unmanaged() {
    return new QuestionPublicationEvidence(false, false, false, false, null);
  }
}
