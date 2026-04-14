package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

public record QnaExecutionIntentResult(
    String executionIntentId,
    String mode,
    int signCount,
    SignRequestBundle signRequest,
    boolean existing) {

  public QnaExecutionIntentResult {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (mode == null || mode.isBlank()) {
      throw new Web3InvalidInputException("mode is required");
    }
    if (signCount <= 0) {
      throw new Web3InvalidInputException("signCount must be positive");
    }
  }

  public static QnaExecutionIntentResult from(CreateExecutionIntentResult result) {
    return new QnaExecutionIntentResult(
        result.executionIntentId(),
        result.mode().name(),
        result.signCount(),
        result.signRequest(),
        result.existing());
  }
}
