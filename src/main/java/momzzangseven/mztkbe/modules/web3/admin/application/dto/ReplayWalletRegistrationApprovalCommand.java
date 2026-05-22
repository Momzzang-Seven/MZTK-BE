package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ReplayWalletRegistrationApprovalCommand(
    Long operatorId,
    String registrationId,
    Long transactionId,
    String executionIntentId,
    String reason,
    String evidence) {

  public void validate() {
    validatePositive(operatorId, "operatorId");
    validateOptionalText(registrationId, "registrationId");
    validateOptionalText(executionIntentId, "executionIntentId");
    if (transactionId != null && transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (!hasText(registrationId) && transactionId == null && !hasText(executionIntentId)) {
      throw new Web3InvalidInputException(
          "registrationId, transactionId, or executionIntentId is required");
    }
    validateRequired(reason, "reason");
    validateRequired(evidence, "evidence");
  }

  public String auditTargetId() {
    if (hasText(executionIntentId)) {
      return executionIntentId;
    }
    if (transactionId != null) {
      return String.valueOf(transactionId);
    }
    return registrationId;
  }

  private static void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void validateRequired(String value, String fieldName) {
    if (!hasText(value)) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static void validateOptionalText(String value, String fieldName) {
    if (value != null && value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " must not be blank");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
