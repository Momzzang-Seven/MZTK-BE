package momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce;

public interface LoadSponsorChainNoncePort {

  SponsorChainNonceSnapshot loadSnapshot(long chainId, String fromAddress);

  record SponsorChainNonceSnapshot(
      long chainPendingNonce,
      long chainLatestNonce,
      Long mainPendingNonce,
      Long subPendingNonce,
      Long mainLatestNonce,
      Long subLatestNonce) {}
}
