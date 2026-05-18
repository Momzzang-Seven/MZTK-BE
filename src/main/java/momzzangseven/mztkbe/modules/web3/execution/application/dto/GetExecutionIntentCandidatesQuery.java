package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;

public record GetExecutionIntentCandidatesQuery(
    ExecutionResourceTypeCode resourceType, String resourceId, int limit) {

  public GetExecutionIntentCandidatesQuery {
    if (limit <= 0) {
      limit = 20;
    }
  }
}
