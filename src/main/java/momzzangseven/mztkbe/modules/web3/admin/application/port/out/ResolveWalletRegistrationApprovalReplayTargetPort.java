package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationApprovalReplayTarget;

public interface ResolveWalletRegistrationApprovalReplayTargetPort {

  Optional<WalletRegistrationApprovalReplayTarget> resolveByRegistrationId(String registrationId);

  Optional<WalletRegistrationApprovalReplayTarget> resolveByTransactionId(Long transactionId);

  Optional<WalletRegistrationApprovalReplayTarget> resolveByExecutionIntentId(
      String executionIntentId);
}
