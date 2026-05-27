package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

public record SponsorChainNonceSnapshotResult(
    long chainPendingNonce,
    long chainLatestNonce,
    Long mainPendingNonce,
    Long subPendingNonce,
    Long mainLatestNonce,
    Long subLatestNonce) {}
