package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;

public interface UnlinkWalletUseCase {

  /**
   * Unlink the wallet
   *
   * @param command unlink command
   */
  void execute(UnlinkWalletCommand command);
}
