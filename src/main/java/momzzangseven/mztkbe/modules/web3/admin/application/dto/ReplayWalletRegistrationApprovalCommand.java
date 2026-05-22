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
    validateOptionalText(
        registrationId,
        "registrationId",
        ReplayWalletRegistrationApprovalInputLimits.REGISTRATION_ID_MAX_LENGTH);
    validateOptionalText(
        executionIntentId,
        "executionIntentId",
        ReplayWalletRegistrationApprovalInputLimits.EXECUTION_INTENT_ID_MAX_LENGTH);
    if (transactionId != null && transactionId <= 0) {
      throw new Web3InvalidInputException("transactionId must be positive");
    }
    if (!hasText(registrationId) && transactionId == null && !hasText(executionIntentId)) {
      throw new Web3InvalidInputException(
          "registrationId, transactionId, or executionIntentId is required");
    }
    validateRequired(
        reason, "reason", ReplayWalletRegistrationApprovalInputLimits.REASON_MAX_LENGTH);
    validateRequired(
        evidence, "evidence", ReplayWalletRegistrationApprovalInputLimits.EVIDENCE_MAX_LENGTH);
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

  private static void validateRequired(String value, String fieldName, int maxLength) {
    if (!hasText(value)) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
    validateLength(value, fieldName, maxLength);
  }

  private static void validateOptionalText(String value, String fieldName, int maxLength) {
    if (value != null && value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " must not be blank");
    }
    if (value != null) {
      validateLength(value, fieldName, maxLength);
    }
  }

  private static void validateLength(String value, String fieldName, int maxLength) {
    if (value.length() > maxLength) {
      throw new Web3InvalidInputException(fieldName + " must not exceed " + maxLength);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
