package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentCleanupView;

public interface GetExecutionIntentCleanupViewUseCase {

  List<ExecutionIntentCleanupView> getCleanupViewsByIds(List<Long> intentIds);
}
