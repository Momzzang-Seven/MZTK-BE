package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.DeleteWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.DeleteWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeleteWalletService implements DeleteWalletUseCase {

  private final LoadWalletPort loadWalletPort;
  private final SaveWalletPort saveWalletPort;

  @Override
  public void execute(DeleteWalletCommand command) {
    log.info("Deleting wallet: userId={}, walletId={}", command.userId(), command.walletId());

    // 1. Validate
    command.validate();

    // 2. Load wallet
    UserWallet wallet =
        loadWalletPort
            .findById(command.walletId())
            .orElseThrow(() -> new WalletNotFoundException(command.walletId()));

    // 3. Check ownership
    if (!wallet.belongsTo(command.userId())) {
      log.warn(
          "Unauthorized wallet access: userId={}, walletId={}",
          command.userId(),
          command.walletId());
      throw new UnauthorizedWalletAccessException(command.walletId(), command.userId());
    }

    // 4. Deactivate wallet (soft delete)
    UserWallet deactivatedWallet = wallet.deactivate();
    saveWalletPort.save(deactivatedWallet);

    log.info(
        "Wallet deleted successfully: walletId={}, userId={}",
        command.walletId(),
        command.userId());
  }
}
