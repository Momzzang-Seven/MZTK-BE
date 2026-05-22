package momzzangseven.mztkbe.modules.web3.admin.application.dto;

public record ReplayWalletRegistrationApprovalResult(
    String outcome,
    boolean replayInvoked,
    String registrationId,
    Long transactionId,
    String executionIntentId,
    String executionIntentStatus,
    String transactionStatus,
    String walletRegistrationStatus,
    String walletLastErrorCode,
    String walletLastErrorReason) {}
