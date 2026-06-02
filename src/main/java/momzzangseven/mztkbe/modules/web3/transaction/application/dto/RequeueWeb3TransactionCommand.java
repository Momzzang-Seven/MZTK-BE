package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record RequeueWeb3TransactionCommand(
    Long operatorId, Long transactionId, String reason, String evidence) {

  public void validate() {
    validatePositive(operatorId, "operatorId");
    validatePositive(transactionId, "transactionId");
    validateRequired(reason, "reason");
    validateRequired(evidence, "evidence");
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void validateRequired(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }
}
