package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public record UpdateAnswerCommentCommand(
    Long answerId, Long commentId, Long userId, String content) {
  public UpdateAnswerCommentCommand {
    if (answerId == null
        || commentId == null
        || userId == null
        || content == null
        || content.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }
  }
}
