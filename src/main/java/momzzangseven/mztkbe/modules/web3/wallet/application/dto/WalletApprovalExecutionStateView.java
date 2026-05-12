package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

import java.time.LocalDateTime;

public record WalletApprovalExecutionStateView(
    String resourceType,
    String resourceId,
    String resourceStatus,
    String actionType,
    String executionIntentId,
    String executionIntentStatus,
    LocalDateTime expiresAt,
    String mode,
    int signCount,
    WalletApprovalSignRequestBundle signRequest,
    Long transactionId,
    String transactionStatus,
    String txHash) {}
