package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/** Command for creating or loading an idempotent LEVEL_UP_REWARD tx intent. */
public record CreateLevelUpRewardTxIntentCommand(
    Long userId,
    Long levelUpHistoryId,
    String idempotencyKey,
    EvmAddress fromAddress,
    EvmAddress toAddress,
    BigInteger amountWei) {

  public CreateLevelUpRewardTxIntentCommand {
    validate(userId, levelUpHistoryId, idempotencyKey, fromAddress, toAddress, amountWei);
  }

  public String referenceId() {
    return String.valueOf(levelUpHistoryId);
  }

  private static void validate(
      Long userId,
      Long levelUpHistoryId,
      String idempotencyKey,
      EvmAddress fromAddress,
      EvmAddress toAddress,
      BigInteger amountWei) {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.USER_ID_POSITIVE);
    }
    if (levelUpHistoryId == null || levelUpHistoryId <= 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.LEVEL_UP_HISTORY_ID_POSITIVE);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.IDEMPOTENCY_KEY_REQUIRED);
    }
    if (fromAddress == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.FROM_ADDRESS_REQUIRED);
    }
    if (toAddress == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.TO_ADDRESS_REQUIRED);
    }
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.AMOUNT_WEI_NON_NEGATIVE);
    }
  }
}
