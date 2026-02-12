package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class CommentNotFoundException extends CommentException {
  public CommentNotFoundException() {
    super(ErrorCode.COMMENT_NOT_FOUND);
  }
}
