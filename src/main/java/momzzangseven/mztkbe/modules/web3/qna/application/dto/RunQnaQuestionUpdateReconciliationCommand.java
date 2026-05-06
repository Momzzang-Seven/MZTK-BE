package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record RunQnaQuestionUpdateReconciliationCommand(Integer limit) {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  public int normalizedLimit() {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    if (limit <= 0) {
      throw new Web3InvalidInputException("limit must be positive");
    }
    return Math.min(limit, MAX_LIMIT);
  }
}
