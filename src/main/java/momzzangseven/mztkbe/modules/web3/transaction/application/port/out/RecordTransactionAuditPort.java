package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Collections;
import java.util.Map;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TransactionAuditEventType;

/** Port for append-only transaction audit logging. */
public interface RecordTransactionAuditPort {

  void record(AuditCommand command);

  record AuditCommand(
      Long transactionId,
      Web3TransactionAuditEventType eventType,
      String rpcAlias,
      Map<String, Object> detail) {

    public AuditCommand {
      if (detail == null) {
        detail = Collections.emptyMap();
      }
      validate(transactionId, eventType, detail);
    }

    private static void validate(
        Long transactionId, Web3TransactionAuditEventType eventType, Map<String, Object> detail) {
      if (transactionId == null || transactionId <= 0) {
        throw new Web3InvalidInputException("transactionId must be positive");
      }
      if (eventType == null) {
        throw new Web3InvalidInputException("eventType is required");
      }
      if (detail == null) {
        throw new Web3InvalidInputException("detail is required");
      }
    }
  }
}
