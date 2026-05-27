package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;

public record ExecutionTransactionRecordResult(
    Long transactionId,
    TransactionStatus status,
    String txHash,
    Long chainId,
    String fromAddress,
    Long nonce) {

  public ExecutionTransactionRecordResult(
      Long transactionId, TransactionStatus status, String txHash) {
    this(transactionId, status, txHash, null, null, null);
  }
}
