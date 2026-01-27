package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.DeactivateWalletCommand;

public interface DeactivateWalletUseCase {

  /**
   * Delete (unlink) wallet
   *
   * @param command delete command
   */
  void execute(DeactivateWalletCommand command);
}
