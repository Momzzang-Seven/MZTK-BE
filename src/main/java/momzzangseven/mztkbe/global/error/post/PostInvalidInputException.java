package momzzangseven.mztkbe.global.error.post;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** 게시글 관련 입력값이 비즈니스 규칙에 맞지 않을 때 발생하는 예외 (예: 제목 누락, 질문글인데 보상금 0원 등) */
public class PostInvalidInputException extends BusinessException {

  public PostInvalidInputException(String message) {
    super(ErrorCode.INVALID_POST_INPUT, message);
  }
}
