package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.time.LocalDateTime;

public record MarketplaceAdminExecutionView(
    Long reservationId,
    String actionType,
    String orderKey,
    String reservationStatus,
    String escrowStatus,
    ExecutionIntent executionIntent,
    Execution execution,
    String adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    boolean existing) {

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {}

  public record Execution(String mode, boolean requiresUserSignature, String authorityModel) {}
}
