package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;

public record ForceQnaAdminRefundResult(QnaExecutionIntentResult executionIntent) {

  public ForceQnaAdminRefundResult {
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
  }
}
