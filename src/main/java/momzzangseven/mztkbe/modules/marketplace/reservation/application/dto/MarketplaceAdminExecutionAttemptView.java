package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceAdminAttemptFailureStage;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;

public record MarketplaceAdminExecutionAttemptView(
    Long actionStateId,
    ReservationActionStateStatus attemptStatus,
    MarketplaceAdminAttemptFailureStage failureStage,
    String executionIntentId,
    String executionStatus,
    MarketplaceAdminExecutionPhase adminExecutionPhase,
    String txHash,
    String failureReason,
    String errorCode,
    String evidenceErrorCode,
    Boolean retryable,
    LocalDateTime finishedAt) {}
