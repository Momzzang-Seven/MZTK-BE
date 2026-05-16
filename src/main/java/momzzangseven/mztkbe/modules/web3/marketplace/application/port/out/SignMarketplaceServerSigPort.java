package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

/** Output port that signs MarketplaceEscrow server-authorized user actions. */
public interface SignMarketplaceServerSigPort {

  MarketplaceServerSigResult sign(MarketplaceServerSigPreimage preimage);
}
