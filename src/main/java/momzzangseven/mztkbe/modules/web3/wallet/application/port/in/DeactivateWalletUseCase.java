package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.DeleteWalletCommand;

public interface DeleteWalletUseCase {

  /**
   * Delete (deactivate) wallet
   *
   * @param command delete command
   */
  void execute(DeleteWalletCommand command);
}
