package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;

public record CreateLevelUpRewardTransactionIntentResult(
    TransactionStatus status, String txHash, String failureReason) {}
