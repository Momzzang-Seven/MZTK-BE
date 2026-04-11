package momzzangseven.mztkbe.modules.web3.qna.domain.vo;

public enum QnaQuestionState {
  CREATED(1000),
  ANSWERED(1100),
  PAID_OUT(2100),
  ADMIN_SETTLED(4000),
  DELETED(5000),
  DELETED_WITH_ANSWERS(5100);

  private final int code;

  QnaQuestionState(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public static QnaQuestionState fromCode(int code) {
    for (QnaQuestionState value : values()) {
      if (value.code == code) {
        return value;
      }
    }
    throw new IllegalArgumentException("unsupported qna question state code: " + code);
  }
}
