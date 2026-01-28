package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;

/**
 * Register wallet UseCase
 *
 * <p>Verifies challenge signature and registers wallet to user account.
 */
public interface RegisterWalletUseCase {

  /**
   * Register wallet after signature verification
   *
   * @param command registration command
   * @return registration result
   */
  RegisterWalletResult execute(RegisterWalletCommand command);
}
