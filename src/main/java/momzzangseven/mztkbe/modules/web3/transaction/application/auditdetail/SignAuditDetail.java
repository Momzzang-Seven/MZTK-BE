package momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.application.support.AuditDetailBuilder;

public record SignAuditDetail(long nonce, String txHash) implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create().put("nonce", nonce).put("txHash", txHash).build();
  }
}
