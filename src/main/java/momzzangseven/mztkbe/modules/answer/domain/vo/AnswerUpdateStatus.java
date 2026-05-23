package momzzangseven.mztkbe.modules.answer.domain.vo;

public enum AnswerUpdateStatus {
  PREPARING,
  INTENT_BOUND,
  PREPARATION_FAILED,
  CONFIRMED,
  FAILED,
  STALE,
  DISCARDED,
  RECONCILIATION_REQUIRED;

  public boolean blocksFollowUpMutation() {
    return this == PREPARING
        || this == INTENT_BOUND
        || this == PREPARATION_FAILED
        || this == FAILED
        || this == RECONCILIATION_REQUIRED;
  }
}
