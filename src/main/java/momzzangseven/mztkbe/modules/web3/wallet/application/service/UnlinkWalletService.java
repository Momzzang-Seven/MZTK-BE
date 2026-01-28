package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.UnlinkWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wallet unlink service
 *
 * <p>Handles user-requested wallet unlinking (soft delete).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UnlinkWalletService implements UnlinkWalletUseCase {

  private final LoadWalletPort loadWalletPort;
  private final SaveWalletPort saveWalletPort;
  private final RecordWalletEventPort eventPort;

  @Override
  public void execute(UnlinkWalletCommand command) {
    log.info(
        "Unlinking wallet: userId={}, walletAddress={}", command.userId(), command.walletAddress());

    // 1. Validate
    command.validate();

    // 2. Load active wallet by address
    List<UserWallet> wallets =
        loadWalletPort.findWalletsByUserIdAndStatus(command.userId(), WalletStatus.ACTIVE);

    if (wallets.isEmpty()) {
      throw new WalletNotFoundException(command.walletAddress());
    }

    // 3. Check ownership
    UserWallet wallet = wallets.get(0);

    if (!wallet.belongsTo(command.userId())) {
      log.warn(
          "Unauthorized wallet access: userId={}, walletAddress={}",
          command.userId(),
          command.walletAddress());
      throw new UnauthorizedWalletAccessException(wallet.getId(), command.userId());
    }

    // 4. Unlink the wallet (soft delete)
    UserWallet unlinkedWallet = wallet.unlink();
    UserWallet savedWallet = saveWalletPort.save(unlinkedWallet);

    // 5. Record the event
    eventPort.record(
            WalletEvent.unlinked(
                    savedWallet.getWalletAddress(),
                    savedWallet.getUserId(),
                    Map.of(
                            "source", "application",
                            "action", "unlink the wallet")));

    log.info(
        "Wallet unlinked successfully: walletId={}, walletAddress={}, userId={}",
        wallet.getId(),
        command.walletAddress(),
        command.userId());
  }
}
