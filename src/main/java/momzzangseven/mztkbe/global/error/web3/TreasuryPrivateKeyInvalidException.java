package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;

public class TreasuryPrivateKeyInvalidException extends BusinessException {

  public TreasuryPrivateKeyInvalidException(String message) {
    super(Web3ErrorCode.WEB3_TREASURY_PRIVATE_KEY_INVALID, message);
  }
}
