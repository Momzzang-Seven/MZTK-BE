package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record MarketplaceAdminExecutionResult(
    Long reservationId,
    String actionType,
    String orderKey,
    ReservationStatus reservationStatus,
    ReservationEscrowStatus escrowStatus,
    ExecutionIntent executionIntent,
    Execution execution,
    MarketplaceAdminExecutionPhase adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    boolean existing) {

  public MarketplaceAdminExecutionResult {
    if (reservationId == null || reservationId <= 0) {
      throw new IllegalArgumentException("reservationId must be positive");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new IllegalArgumentException("actionType is required");
    }
    if (orderKey == null || orderKey.isBlank()) {
      throw new IllegalArgumentException("orderKey is required");
    }
    if (reservationStatus == null) {
      throw new IllegalArgumentException("reservationStatus is required");
    }
    if (escrowStatus == null) {
      throw new IllegalArgumentException("escrowStatus is required");
    }
    if (executionIntent == null) {
      throw new IllegalArgumentException("executionIntent is required");
    }
    if (execution == null) {
      throw new IllegalArgumentException("execution is required");
    }
    if (adminExecutionPhase == null) {
      throw new IllegalArgumentException("adminExecutionPhase is required");
    }
  }

  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {

    public ExecutionIntent {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("executionIntent.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new IllegalArgumentException("executionIntent.status is required");
      }
      if (expiresAt == null) {
        throw new IllegalArgumentException("executionIntent.expiresAt is required");
      }
    }
  }

  public record Execution(String mode, boolean requiresUserSignature, String authorityModel) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new IllegalArgumentException("execution.mode is required");
      }
      if (authorityModel == null || authorityModel.isBlank()) {
        throw new IllegalArgumentException("execution.authorityModel is required");
      }
    }
  }
}
