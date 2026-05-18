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
    long expiresAtEpochSeconds,
    String mode,
    int signCount,
    WalletApprovalSignRequestBundle signRequest,
    String signRequestUnavailableReason,
    Long transactionId,
    String transactionStatus,
    String txHash) {}
