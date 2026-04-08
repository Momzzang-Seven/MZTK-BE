package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionAuditEventType;

public record ExecutionTransactionAuditCommand(
    Long transactionId,
    TransactionAuditEventType eventType,
    String rpcAlias,
    Map<String, Object> detail) {}
