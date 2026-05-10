package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

import java.util.EnumSet;

public enum QnaQuestionUpdateStateStatus {
  PREPARING,
  PREPARATION_FAILED,
  INTENT_BOUND,
  CONFIRMED,
  STALE;

  public boolean isNonTerminal() {
    return this == PREPARING || this == PREPARATION_FAILED || this == INTENT_BOUND;
  }

  public boolean isTerminal() {
    return this == CONFIRMED || this == STALE;
  }

  public boolean isBindable() {
    return this == PREPARING || this == PREPARATION_FAILED;
  }

  public boolean isSupersedableByNewPreparation() {
    return isNonTerminal();
  }

  public static EnumSet<QnaQuestionUpdateStateStatus> nonTerminalStatuses() {
    return EnumSet.of(PREPARING, PREPARATION_FAILED, INTENT_BOUND);
  }

  public static EnumSet<QnaQuestionUpdateStateStatus> bindableStatuses() {
    return EnumSet.of(PREPARING, PREPARATION_FAILED);
  }

  public static EnumSet<QnaQuestionUpdateStateStatus> supersedableByNewPreparationStatuses() {
    return EnumSet.of(PREPARING, PREPARATION_FAILED, INTENT_BOUND);
  }
}
