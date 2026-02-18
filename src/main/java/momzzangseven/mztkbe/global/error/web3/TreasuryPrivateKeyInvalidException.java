package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class TreasuryPrivateKeyInvalidException extends BusinessException {

  public TreasuryPrivateKeyInvalidException(String message) {
    super(ErrorCode.WEB3_TREASURY_PRIVATE_KEY_INVALID, message);
  }
}
