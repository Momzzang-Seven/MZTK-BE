package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.audit.AuditDetailBuilder;

public record SignAuditDetail(long nonce, String txHash) implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create().put("nonce", nonce).put("txHash", txHash).build();
  }
}
