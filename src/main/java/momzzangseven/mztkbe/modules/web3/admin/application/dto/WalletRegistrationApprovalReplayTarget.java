package momzzangseven.mztkbe.modules.web3.admin.application.dto;

public record WalletRegistrationApprovalReplayTarget(
    String resolutionOutcome,
    String executionIntentId,
    String resourceType,
    String registrationId,
    String actionType,
    String executionIntentStatus,
    Long transactionId,
    String transactionStatus,
    String txHash) {

  public WalletRegistrationApprovalReplayTarget(
      String executionIntentId,
      String resourceType,
      String registrationId,
      String actionType,
      String executionIntentStatus,
      Long transactionId,
      String transactionStatus,
      String txHash) {
    this(
        "RESOLVED",
        executionIntentId,
        resourceType,
        registrationId,
        actionType,
        executionIntentStatus,
        transactionId,
        transactionStatus,
        txHash);
  }
}
