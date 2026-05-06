package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

public enum QnaQuestionUpdateStateStatus {
  PREPARING,
  PREPARATION_FAILED,
  INTENT_BOUND,
  CONFIRMED,
  STALE;

  public boolean isNonTerminal() {
    return this == PREPARING || this == PREPARATION_FAILED || this == INTENT_BOUND;
  }
}
