package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.PrecheckMarketplacePurchaseCommand;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.PrecheckMarketplacePurchaseUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceActiveWalletPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplacePurchaseConfigPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * Application precheck for marketplace purchase invariants before reservation state is retained.
 */
public class PrecheckMarketplacePurchaseService implements PrecheckMarketplacePurchaseUseCase {

  private final LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort;
  private final LoadMarketplacePurchaseConfigPort loadMarketplacePurchaseConfigPort;
  private final PrecheckMarketplacePurchaseFundingPort precheckMarketplacePurchaseFundingPort;

  public PrecheckMarketplacePurchaseService(
      LoadMarketplaceActiveWalletPort loadMarketplaceActiveWalletPort,
      LoadMarketplacePurchaseConfigPort loadMarketplacePurchaseConfigPort,
      PrecheckMarketplacePurchaseFundingPort precheckMarketplacePurchaseFundingPort) {
    this.loadMarketplaceActiveWalletPort = loadMarketplaceActiveWalletPort;
    this.loadMarketplacePurchaseConfigPort = loadMarketplacePurchaseConfigPort;
    this.precheckMarketplacePurchaseFundingPort = precheckMarketplacePurchaseFundingPort;
  }

  @Override
  public void precheck(PrecheckMarketplacePurchaseCommand command) {
    if (command.buyerUserId().equals(command.trainerUserId())) {
      throw new Web3TransferException(
          ErrorCode.MARKETPLACE_CANNOT_BUY_OWN_CLASS,
          "buyer cannot purchase own marketplace class",
          false);
    }
    if (!command.signedAmount().equals(BigInteger.valueOf(command.bookedPriceAmountKrw()))) {
      throw new Web3TransferException(
          ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH,
          "signed amount does not match marketplace class price",
          false);
    }
    String buyerWallet = activeWallet(command.buyerUserId());
    String trainerWallet = activeWallet(command.trainerUserId());
    if (buyerWallet.equals(trainerWallet)) {
      throw new Web3TransferException(
          ErrorCode.MARKETPLACE_CANNOT_BUY_OWN_CLASS,
          "buyer wallet cannot purchase a class owned by the same trainer wallet",
          false);
    }
    var config = loadMarketplacePurchaseConfigPort.loadPurchaseConfig();
    precheckMarketplacePurchaseFundingPort.precheck(
        new PrecheckMarketplacePurchaseFundingPort.PurchaseFundingCheck(
            buyerWallet,
            trainerWallet,
            config.escrowContractAddress(),
            config.tokenAddress(),
            command.signedAmount()));
  }

  private String activeWallet(Long userId) {
    return loadMarketplaceActiveWalletPort
        .loadActiveWalletAddress(userId)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(() -> new WalletNotConnectedException(userId));
  }
}
