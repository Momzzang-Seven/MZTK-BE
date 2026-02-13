package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.audit.AuditDetailBuilder;

public record PrevalidateAuditDetail(Map<String, Object> base, boolean ok, String failureReason)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create()
        .putAll(base)
        .put("ok", ok)
        .put("failureReason", failureReason)
        .build();
  }
}
