package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.List;

public interface ExecutionIntentCleanupProtectionPort {

  List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds);
}
