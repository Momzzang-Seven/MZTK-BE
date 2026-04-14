package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

/** Batch lookup query for QnA execution resume summaries. */
public record GetQnaExecutionResumeBatchViewQuery(
    QnaExecutionResourceType resourceType, List<Long> resourceIds) {

  public GetQnaExecutionResumeBatchViewQuery {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceIds == null || resourceIds.isEmpty()) {
      throw new Web3InvalidInputException("resourceIds is required");
    }
    resourceIds = List.copyOf(resourceIds);
    if (resourceIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new Web3InvalidInputException("resourceIds must contain positive ids only");
    }
  }
}
