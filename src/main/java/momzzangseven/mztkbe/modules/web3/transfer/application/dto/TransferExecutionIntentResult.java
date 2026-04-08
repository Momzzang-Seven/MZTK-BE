package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.time.LocalDateTime;

/** Transfer-owned execution intent view exposed to transfer callers. */
public record TransferExecutionIntentResult(
    String resourceType,
    String resourceId,
    String resourceStatus,
    String executionIntentId,
    String executionIntentStatus,
    LocalDateTime expiresAt,
    String mode,
    int signCount,
    TransferSignRequestBundle signRequest,
    boolean existing,
    Long transactionId,
    String transactionStatus,
    String txHash) {}
