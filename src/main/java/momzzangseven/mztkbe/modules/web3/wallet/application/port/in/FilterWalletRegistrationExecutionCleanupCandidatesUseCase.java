package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationExecutionCleanupCandidate;

public interface FilterWalletRegistrationExecutionCleanupCandidatesUseCase {

  List<Long> filterDeletableFinalizedIntentIds(
      List<WalletRegistrationExecutionCleanupCandidate> candidates);
}
