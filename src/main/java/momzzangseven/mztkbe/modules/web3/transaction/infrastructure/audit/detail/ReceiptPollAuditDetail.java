package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.audit.AuditDetailBuilder;

public record ReceiptPollAuditDetail(
    int attempt, long elapsedSeconds, boolean found, boolean rpcError, String failureReason)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create()
        .put("attempt", attempt)
        .put("elapsedSeconds", elapsedSeconds)
        .put("result", found ? "receipt_found" : "receipt_missing")
        .put("rpcError", rpcError)
        .put("failureReason", failureReason)
        .build();
  }
}
