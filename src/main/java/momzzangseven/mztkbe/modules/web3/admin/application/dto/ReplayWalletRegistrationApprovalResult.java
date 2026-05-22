package momzzangseven.mztkbe.modules.web3.admin.application.dto;

public record ReplayWalletRegistrationApprovalResult(
    String outcome,
    boolean replayInvoked,
    String registrationId,
    Long transactionId,
    String txHash,
    String executionIntentId,
    String executionIntentStatus,
    String transactionStatus,
    String walletRegistrationStatus,
    boolean newerWalletRegistrationExists,
    String walletLastErrorCode,
    String walletLastErrorReason) {}
