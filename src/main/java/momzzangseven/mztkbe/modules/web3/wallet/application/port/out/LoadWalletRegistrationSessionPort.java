package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Read port for wallet registration sessions. */
public interface LoadWalletRegistrationSessionPort {

  Optional<WalletRegistrationSession> loadByPublicId(String publicId);

  Optional<WalletRegistrationSession> loadByPublicIdAndUserId(String publicId, Long userId);

  Optional<WalletRegistrationSession> loadByLatestExecutionIntentId(String executionIntentId);

  Optional<WalletRegistrationSession> loadLatestNonTerminalByUserId(Long userId);

  Optional<WalletRegistrationSession> loadLatestNonTerminalByWalletAddress(String walletAddress);

  Optional<WalletRegistrationSession> loadLatestNonTerminalByUserIdAndWalletAddress(
      Long userId, String walletAddress);

  boolean existsNewerByUserIdOrWalletAddress(Long userId, String walletAddress, Long sessionId);
}
