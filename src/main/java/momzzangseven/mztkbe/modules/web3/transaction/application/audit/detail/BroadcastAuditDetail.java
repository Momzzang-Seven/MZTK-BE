package momzzangseven.mztkbe.modules.web3.transaction.application.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.AuditDetailBuilder;

public record BroadcastAuditDetail(boolean success, String txHash, String failureReason)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create()
        .put("success", success)
        .put("txHash", txHash)
        .put("failureReason", failureReason)
        .build();
  }
}
