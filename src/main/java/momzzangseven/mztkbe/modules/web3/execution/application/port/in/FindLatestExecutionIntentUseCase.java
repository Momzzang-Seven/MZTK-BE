package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.FindLatestExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.FindLatestExecutionIntentResult;

public interface FindLatestExecutionIntentUseCase {

  Optional<FindLatestExecutionIntentResult> execute(FindLatestExecutionIntentQuery query);
}
