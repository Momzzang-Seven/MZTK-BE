package momzzangseven.mztkbe.modules.answer.domain.vo;

public enum AnswerPublicationStatus {
  PENDING,
  VISIBLE,
  FAILED,
  RECONCILIATION_REQUIRED;

  public boolean isPubliclyVisible() {
    return this == VISIBLE;
  }

  public boolean isOwnerVisible() {
    return this == PENDING || this == FAILED || this == RECONCILIATION_REQUIRED;
  }
}
