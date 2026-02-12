package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public record UpdateCommentCommand(Long commentId, Long userId, String content) {
  public UpdateCommentCommand {
    validate(commentId, userId, content);
  }

  private void validate(Long commentId, Long userId, String content) {
    if (commentId == null || userId == null || content == null || content.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }
    if (content.length() > 1000) {
      throw new BusinessException(ErrorCode.COMMENT_TOO_LONG);
    }
  }
}
