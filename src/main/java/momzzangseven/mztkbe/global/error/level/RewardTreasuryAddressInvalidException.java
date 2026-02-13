package momzzangseven.mztkbe.global.error.level;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class RewardTreasuryAddressInvalidException extends BusinessException {

  public RewardTreasuryAddressInvalidException(String treasuryAddress) {
    super(
        ErrorCode.REWARD_TREASURY_ADDRESS_INVALID,
        "Invalid treasury address configuration: " + treasuryAddress);
  }
}
