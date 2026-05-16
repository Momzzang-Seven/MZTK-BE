package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import java.util.List;

public interface FilterMarketplaceExecutionCleanupCandidatesUseCase {

  List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds);
}
