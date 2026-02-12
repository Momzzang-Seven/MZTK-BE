package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

@Builder
public record MarkTransactionSucceededResult(
    Long transactionId,
    Web3TxStatus status,
    Web3TxStatus previousStatus,
    String txHash,
    String explorerUrl) {}
