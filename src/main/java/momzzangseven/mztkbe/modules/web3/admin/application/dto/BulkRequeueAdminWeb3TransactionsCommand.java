package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.util.Comparator;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record BulkRequeueAdminWeb3TransactionsCommand(
    Long operatorId, List<Long> transactionIds, String reason, String evidence) {

  public static final int MAX_TRANSACTION_IDS = 200;

  public void validate() {
    validatePositive(operatorId, "operatorId");
    if (transactionIds == null || transactionIds.isEmpty()) {
      throw new Web3InvalidInputException("transactionIds is required");
    }
    if (requestedCount() > MAX_TRANSACTION_IDS) {
      throw new Web3InvalidInputException("transactionIds must not exceed " + MAX_TRANSACTION_IDS);
    }
    if (uniqueTransactionIds().size() > MAX_TRANSACTION_IDS) {
      throw new Web3InvalidInputException(
          "unique transactionIds must not exceed " + MAX_TRANSACTION_IDS);
    }
    for (Long transactionId : uniqueTransactionIds()) {
      validatePositive(transactionId, "transactionId");
    }
    validateRequired(reason, "reason");
    validateRequired(evidence, "evidence");
  }

  public int requestedCount() {
    return transactionIds == null ? 0 : transactionIds.size();
  }

  public List<Long> uniqueTransactionIds() {
    if (transactionIds == null) {
      return List.of();
    }
    return transactionIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
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
