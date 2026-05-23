package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.PrecheckMarketplacePurchaseFundingPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(MarketplaceContractCallSupport.class)
public class MarketplacePurchaseFundingPrecheckAdapter
    implements PrecheckMarketplacePurchaseFundingPort {

  private final MarketplaceContractCallSupport marketplaceContractCallSupport;

  @Override
  public void precheck(PurchaseFundingCheck check) {
    if (!marketplaceContractCallSupport.isSupportedToken(
        check.escrowContractAddress(), check.tokenAddress())) {
      throw new Web3InvalidInputException(
          "MarketplaceEscrow does not support configured marketplace token");
    }
    if (marketplaceContractCallSupport
            .loadBalance(check.buyerWalletAddress(), check.tokenAddress())
            .compareTo(check.priceBaseUnits())
        < 0) {
      throw new Web3TransferException(
          ErrorCode.MARKETPLACE_INSUFFICIENT_TOKEN_BALANCE,
          "insufficient token balance for marketplace purchase",
          false);
    }
    if (marketplaceContractCallSupport
            .loadAllowance(
                check.buyerWalletAddress(), check.escrowContractAddress(), check.tokenAddress())
            .compareTo(check.priceBaseUnits())
        < 0) {
      throw new Web3TransferException(
          ErrorCode.MARKETPLACE_INSUFFICIENT_ALLOWANCE,
          "insufficient allowance for marketplace purchase",
          false);
    }
  }
}
