package momzzangseven.mztkbe.modules.web3.admin.application.dto;

public record WalletRegistrationRecoveryStateView(
    String registrationId,
    Long userId,
    String walletAddress,
    String status,
    String latestExecutionIntentId,
    Long latestTransactionId,
    String latestTransactionHash,
    String lastErrorCode,
    String lastErrorReason,
    Long registeredWalletId) {}
