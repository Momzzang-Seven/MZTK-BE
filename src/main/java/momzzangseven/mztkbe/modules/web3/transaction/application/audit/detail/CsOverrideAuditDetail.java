package momzzangseven.mztkbe.modules.web3.transaction.application.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.AuditDetailBuilder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record CsOverrideAuditDetail(
    Long operatorId,
    Web3TxStatus fromStatus,
    Web3TxStatus toStatus,
    String reason,
    String evidence,
    String explorerUrl,
    String txHash,
    Boolean receiptFound,
    Boolean receiptSuccess,
    String receiptFailureReason)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create()
        .put("operatorId", operatorId)
        .put("fromStatus", fromStatus)
        .put("toStatus", toStatus)
        .put("reason", reason)
        .put("evidence", evidence)
        .put("explorerUrl", explorerUrl)
        .put("txHash", txHash)
        .put("receiptFound", receiptFound)
        .put("receiptSuccess", receiptSuccess)
        .put("receiptFailureReason", receiptFailureReason)
        .build();
  }
}
