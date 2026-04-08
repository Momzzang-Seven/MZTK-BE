package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionReferenceType;

public record HandleTransferSucceededCommand(
    Long transactionId,
    String idempotencyKey,
    TransferTransactionReferenceType referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String txHash) {}
