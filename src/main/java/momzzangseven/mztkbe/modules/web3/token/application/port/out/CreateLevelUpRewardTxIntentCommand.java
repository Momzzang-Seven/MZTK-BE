package momzzangseven.mztkbe.modules.web3.token.application.port.out;

import java.math.BigInteger;

/** Command for creating or loading an idempotent LEVEL_UP_REWARD tx intent. */
public record CreateLevelUpRewardTxIntentCommand(
    Long userId,
    Long levelUpHistoryId,
    String idempotencyKey,
    String fromAddress,
    String toAddress,
    BigInteger amountWei) {

  public String referenceId() {
    return String.valueOf(levelUpHistoryId);
  }
}
