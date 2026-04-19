package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunInternalExecutionBatchResult;

public interface RunInternalExecutionBatchUseCase {

  RunInternalExecutionBatchResult runBatch(Instant now);
}
