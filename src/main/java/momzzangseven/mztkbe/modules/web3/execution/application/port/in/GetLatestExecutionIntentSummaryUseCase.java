package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;

public interface GetLatestExecutionIntentSummaryUseCase {

  Optional<GetLatestExecutionIntentSummaryResult> execute(
      GetLatestExecutionIntentSummaryQuery query);
}
