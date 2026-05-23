package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.math.BigInteger;

/** EIP-712 server-signature preimages for MarketplaceEscrow user actions. */
public sealed interface MarketplaceServerSigPreimage {

  record PurchaseClassPreimage(
      String buyer, String orderKeyHex, String tokenAddress, String trainer, BigInteger price)
      implements MarketplaceServerSigPreimage {}

  record ConfirmClassPreimage(String buyer, String orderKeyHex)
      implements MarketplaceServerSigPreimage {}

  record CancelClassPreimage(String caller, String orderKeyHex)
      implements MarketplaceServerSigPreimage {}
}
