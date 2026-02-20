package momzzangseven.mztkbe.global.error.comment;

import momzzangseven.mztkbe.global.error.ErrorCode;

public class CommentUnauthorizedException extends CommentException {
  public CommentUnauthorizedException() {
    super(ErrorCode.COMMENT_UNAUTHORIZED);
  }
}
