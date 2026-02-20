package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidCommentConfigException extends CommentException {
  public InvalidCommentConfigException() {
    super(ErrorCode.INVALID_COMMENT_HARD_DELETE_CONFIG);
  }
}
