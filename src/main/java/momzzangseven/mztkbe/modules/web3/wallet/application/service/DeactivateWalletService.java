package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.DeactivateWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.DeactivateWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeactivateWalletService implements DeactivateWalletUseCase {

  private final LoadWalletPort loadWalletPort;
  private final SaveWalletPort saveWalletPort;

  @Override
  public void execute(DeactivateWalletCommand command) {
    log.info(
        "Deactivating wallet: userId={}, walletAddress={}",
        command.userId(),
        command.walletAddress());

    // 1. Validate
    command.validate();

    // 2. Load wallet by address
    UserWallet wallet =
        loadWalletPort
            .findByWalletAddress(command.walletAddress())
            .orElseThrow(() -> new WalletNotFoundException());

    // 3. Check ownership
    if (!wallet.belongsTo(command.userId())) {
      log.warn(
          "Unauthorized wallet access: userId={}, walletAddress={}",
          command.userId(),
          command.walletAddress());
      throw new UnauthorizedWalletAccessException(wallet.getId(), command.userId());
    }

    // 4. Deactivate wallet (soft delete)
    UserWallet deactivatedWallet = wallet.deactivate();
    saveWalletPort.save(deactivatedWallet);

    log.info(
        "Wallet deactivated successfully: walletId={}, walletAddress={}, userId={}",
        wallet.getId(),
        command.walletAddress(),
        command.userId());
  }
}
