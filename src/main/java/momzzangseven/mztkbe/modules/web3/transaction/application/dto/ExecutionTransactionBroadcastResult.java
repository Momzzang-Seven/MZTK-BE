package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

public record ExecutionTransactionBroadcastResult(
    boolean success, String txHash, String failureReason, String rpcAlias) {}
