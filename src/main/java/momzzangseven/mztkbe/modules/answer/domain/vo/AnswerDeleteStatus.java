package momzzangseven.mztkbe.modules.answer.domain.vo;

public enum AnswerDeleteStatus {
  PREPARING,
  PENDING,
  FAILED;

  public boolean blocksPublicVisibility() {
    return this == PREPARING || this == PENDING;
  }
}
