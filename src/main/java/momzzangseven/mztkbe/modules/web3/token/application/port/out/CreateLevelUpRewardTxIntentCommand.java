package momzzangseven.mztkbe.modules.web3.token.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.domain.vo.EvmAddress;

/** Command for creating or loading an idempotent LEVEL_UP_REWARD tx intent. */
public record CreateLevelUpRewardTxIntentCommand(
    Long userId,
    Long levelUpHistoryId,
    String idempotencyKey,
    EvmAddress fromAddress,
    EvmAddress toAddress,
    BigInteger amountWei) {

  public CreateLevelUpRewardTxIntentCommand {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (levelUpHistoryId == null || levelUpHistoryId <= 0) {
      throw new Web3InvalidInputException("levelUpHistoryId must be positive");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("idempotencyKey is required");
    }
    if (fromAddress == null) {
      throw new Web3InvalidInputException("fromAddress is required");
    }
    if (toAddress == null) {
      throw new Web3InvalidInputException("toAddress is required");
    }
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }
  }

  public String referenceId() {
    return String.valueOf(levelUpHistoryId);
  }
}
