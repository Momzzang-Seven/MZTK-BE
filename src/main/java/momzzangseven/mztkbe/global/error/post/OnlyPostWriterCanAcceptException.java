package momzzangseven.mztkbe.global.error.post;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class OnlyPostWriterCanAcceptException extends BusinessException {

  public OnlyPostWriterCanAcceptException() {
    super(ErrorCode.ONLY_POST_WRITER_CAN_ACCEPT);
  }
}
