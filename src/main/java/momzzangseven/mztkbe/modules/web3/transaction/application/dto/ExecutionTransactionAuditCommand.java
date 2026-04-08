package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.util.Map;

public record ExecutionTransactionAuditCommand(
    Long transactionId, String eventType, String rpcAlias, Map<String, Object> detail) {}
