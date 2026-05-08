package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.util.List;

public interface QnaExecutionCleanupProtectionQueryPort {

  List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds);
}
