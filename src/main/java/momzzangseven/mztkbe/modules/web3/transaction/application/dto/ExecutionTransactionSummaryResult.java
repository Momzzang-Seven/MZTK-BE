package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;

public record ExecutionTransactionSummaryResult(
    Long transactionId, TransactionStatus status, String txHash) {}
