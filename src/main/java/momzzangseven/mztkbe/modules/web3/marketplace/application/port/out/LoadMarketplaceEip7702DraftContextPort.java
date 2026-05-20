package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

public interface LoadMarketplaceEip7702DraftContextPort {

  MarketplaceEip7702DraftContext load(String authorityAddress);

  record MarketplaceEip7702DraftContext(
      long chainId,
      String delegateTarget,
      long authorityNonce,
      String authorizationPayloadHash,
      long authorizationTtlSeconds) {}
}
