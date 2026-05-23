package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Result counters for one wallet registration recovery batch. */
public record RunWalletRegistrationRecoveryBatchResult(
    int scanned, int recovered, int skipped, int failed) {}
