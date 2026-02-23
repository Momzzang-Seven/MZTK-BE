package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;

public interface MarkTransactionSucceededPort {

  MarkTransactionSucceededResult markSucceeded(
      Long operatorId,
      Long transactionId,
      String txHash,
      String explorerUrl,
      String reason,
      String evidence);
}
