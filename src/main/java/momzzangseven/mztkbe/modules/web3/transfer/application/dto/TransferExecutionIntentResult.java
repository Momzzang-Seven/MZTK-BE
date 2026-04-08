package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionStatus;

/** Transfer-owned execution intent view exposed to transfer callers. */
public record TransferExecutionIntentResult(
    TransferExecutionResourceType resourceType,
    String resourceId,
    TransferExecutionResourceStatus resourceStatus,
    String executionIntentId,
    TransferExecutionIntentStatus executionIntentStatus,
    LocalDateTime expiresAt,
    TransferExecutionMode mode,
    int signCount,
    TransferSignRequestBundle signRequest,
    boolean existing,
    Long transactionId,
    TransferTransactionStatus transactionStatus,
    String txHash) {}
