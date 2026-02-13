package momzzangseven.mztkbe.global.error.signature;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidSignatureException extends BusinessException {

  public InvalidSignatureException() {
    super(ErrorCode.SIGNATURE_INVALID);
  }
}
