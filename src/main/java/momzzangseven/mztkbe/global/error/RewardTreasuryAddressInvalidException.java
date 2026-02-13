package momzzangseven.mztkbe.global.error;

public class RewardTreasuryAddressInvalidException extends BusinessException {

  public RewardTreasuryAddressInvalidException(String treasuryAddress) {
    super(
        ErrorCode.REWARD_TREASURY_ADDRESS_INVALID,
        "Invalid treasury address configuration: " + treasuryAddress);
  }
}
