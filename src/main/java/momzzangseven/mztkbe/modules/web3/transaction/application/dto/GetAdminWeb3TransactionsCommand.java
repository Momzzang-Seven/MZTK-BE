package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record GetAdminWeb3TransactionsCommand(
    Long operatorId,
    String failureReason,
    String status,
    String referenceType,
    String referenceId,
    String txType,
    Integer page,
    Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 50;
  private static final int MAX_SIZE = 100;

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    validateOptionalText(failureReason, "failureReason");
    validateOptionalText(status, "status");
    validateOptionalText(referenceType, "referenceType");
    validateOptionalText(txType, "txType");
    if (page != null && page < 0) {
      throw new Web3InvalidInputException("page must be >= 0");
    }
    if (size != null && size <= 0) {
      throw new Web3InvalidInputException("size must be > 0");
    }
    if (size != null && size > MAX_SIZE) {
      throw new Web3InvalidInputException("size must be <= " + MAX_SIZE);
    }
    if (referenceId != null && referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId must not be blank");
    }
  }

  public int normalizedPage() {
    return page == null ? DEFAULT_PAGE : page;
  }

  public int normalizedSize() {
    return size == null ? DEFAULT_SIZE : size;
  }

  private static void validateOptionalText(String value, String fieldName) {
    if (value != null && value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " must not be blank");
    }
  }
}
