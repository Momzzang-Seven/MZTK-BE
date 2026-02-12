package momzzangseven.mztkbe.modules.web3.transaction.application.auditdetail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.application.support.AuditDetailBuilder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record StateChangeAuditDetail(Web3TxStatus from, Web3TxStatus to)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create().put("from", from).put("to", to).build();
  }
}
