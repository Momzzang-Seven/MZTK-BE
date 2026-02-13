package momzzangseven.mztkbe.modules.web3.transaction.application.command;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;

public record MarkTransactionSucceededCommand(
    Long operatorId,
    Long transactionId,
    String txHash,
    String explorerUrl,
    String reason,
    String evidence) {

  public MarkTransactionSucceededCommand {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (transactionId == null || transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (txHash == null || txHash.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.TX_HASH_REQUIRED);
    }
    if (!txHash.matches("^0x[0-9a-fA-F]{64}$")) {
      throw new Web3InvalidInputException("txHash must be 0x-prefixed 32-byte hex");
    }
    if (explorerUrl == null || explorerUrl.isBlank()) {
      throw new Web3InvalidInputException("explorerUrl is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new Web3InvalidInputException("reason is required");
    }
    if (evidence == null || evidence.isBlank()) {
      throw new Web3InvalidInputException("evidence is required");
    }
  }
}
