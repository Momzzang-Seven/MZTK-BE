package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;

/** Port for append-only transaction audit logging. */
public interface RecordTransactionAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long transactionId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {}
}
