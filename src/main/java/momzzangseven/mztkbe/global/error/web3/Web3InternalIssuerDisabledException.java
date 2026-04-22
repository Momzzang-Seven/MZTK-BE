package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class Web3InternalIssuerDisabledException extends BusinessException {

  public Web3InternalIssuerDisabledException(String message) {
    super(ErrorCode.WEB3_INTERNAL_ISSUER_DISABLED, message);
  }
}
