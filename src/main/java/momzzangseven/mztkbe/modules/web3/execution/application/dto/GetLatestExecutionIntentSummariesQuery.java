package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;

/** Batch lookup query for latest execution summaries by resource ids. */
public record GetLatestExecutionIntentSummariesQuery(
    ExecutionResourceTypeCode resourceType, List<String> resourceIds) {

  public GetLatestExecutionIntentSummariesQuery {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceIds == null || resourceIds.isEmpty()) {
      throw new Web3InvalidInputException("resourceIds is required");
    }
    resourceIds = List.copyOf(resourceIds);
    if (resourceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new Web3InvalidInputException("resourceIds must not contain blank values");
    }
  }
}
