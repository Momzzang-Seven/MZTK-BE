package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class CommentTargetMismatchException extends CommentException {
  public CommentTargetMismatchException() {
    super(ErrorCode.COMMENT_TARGET_MISMATCH);
  }
}
