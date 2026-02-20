package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class CommentPostMismatchException extends CommentException {
  public CommentPostMismatchException() {
    super(ErrorCode.COMMENT_POST_MISMATCH);
  }
}
