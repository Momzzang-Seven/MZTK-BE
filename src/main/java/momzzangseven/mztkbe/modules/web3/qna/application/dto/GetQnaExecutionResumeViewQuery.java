package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

public record GetQnaExecutionResumeViewQuery(
    QnaExecutionResourceType resourceType, Long resourceId) {

  public GetQnaExecutionResumeViewQuery {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId <= 0) {
      throw new Web3InvalidInputException("resourceId must be positive");
    }
  }
}
