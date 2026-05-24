package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;

public record SubmitMarketplaceAdminEscrowResult(
    String executionIntentId,
    String executionIntentStatus,
    String executionMode,
    LocalDateTime expiresAt,
    boolean existing) {}
