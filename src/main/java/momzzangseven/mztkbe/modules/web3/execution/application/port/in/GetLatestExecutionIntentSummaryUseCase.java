package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;

public interface GetLatestExecutionIntentSummaryUseCase {

  Optional<GetLatestExecutionIntentSummaryResult> execute(
      GetLatestExecutionIntentSummaryQuery query);

  default Map<String, GetLatestExecutionIntentSummaryResult> executeBatch(
      GetLatestExecutionIntentSummariesQuery query) {
    Map<String, GetLatestExecutionIntentSummaryResult> results = new LinkedHashMap<>();
    for (String resourceId : query.resourceIds()) {
      execute(new GetLatestExecutionIntentSummaryQuery(query.resourceType(), resourceId))
          .ifPresent(result -> results.put(resourceId, result));
    }
    return results;
  }
}
