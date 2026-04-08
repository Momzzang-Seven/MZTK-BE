package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

public record CreateLevelUpRewardTransactionIntentResult(
    String status, String txHash, String failureReason) {}
