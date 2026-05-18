package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;

public interface LoadWalletApprovalExecutionStatePort {

  Optional<WalletApprovalExecutionStateView> loadByExecutionIntentId(
      Long requesterUserId, String executionIntentId);

  Optional<WalletApprovalExecutionStateView> loadLatestByRegistrationId(String registrationId);
}
