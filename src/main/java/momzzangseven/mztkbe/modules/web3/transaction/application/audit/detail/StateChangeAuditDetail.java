package momzzangseven.mztkbe.modules.web3.transaction.application.audit.detail;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.application.audit.AuditDetailBuilder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public record StateChangeAuditDetail(Web3TxStatus from, Web3TxStatus to)
    implements TransactionAuditDetail {

  @Override
  public Map<String, Object> toMap() {
    return AuditDetailBuilder.create().put("from", from).put("to", to).build();
  }
}
