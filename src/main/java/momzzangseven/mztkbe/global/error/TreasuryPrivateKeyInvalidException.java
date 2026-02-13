package momzzangseven.mztkbe.global.error;

public class TreasuryPrivateKeyInvalidException extends BusinessException {

  public TreasuryPrivateKeyInvalidException(String message) {
    super(ErrorCode.WEB3_TREASURY_PRIVATE_KEY_INVALID, message);
  }
}
