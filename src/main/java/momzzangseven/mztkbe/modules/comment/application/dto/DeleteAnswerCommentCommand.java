package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public record DeleteAnswerCommentCommand(Long answerId, Long commentId, Long userId) {
  public DeleteAnswerCommentCommand {
    if (answerId == null || commentId == null || userId == null) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }
  }
}
