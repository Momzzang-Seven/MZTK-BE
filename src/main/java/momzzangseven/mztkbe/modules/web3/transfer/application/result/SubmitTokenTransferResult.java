package momzzangseven.mztkbe.modules.web3.transfer.application.result;

import lombok.Builder;

@Builder
public record SubmitTokenTransferResult(Long transactionId, String status, String txHash) {}
