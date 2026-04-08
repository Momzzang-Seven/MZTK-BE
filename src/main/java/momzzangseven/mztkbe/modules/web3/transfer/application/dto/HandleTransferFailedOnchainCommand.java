package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

public record HandleTransferFailedOnchainCommand(
    Long transactionId,
    String idempotencyKey,
    String referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String txHash,
    String failureReason) {}
