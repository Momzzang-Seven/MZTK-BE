package momzzangseven.mztkbe.modules.web3.qna.application.port.in;

import java.util.List;

public interface FilterQnaExecutionCleanupCandidatesUseCase {

  List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds);
}
