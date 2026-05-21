package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record ForceMarketplaceAdminExecutionResponseDTO(
    Long reservationId,
    String actionType,
    String orderKey,
    ReservationStatus reservationStatus,
    ReservationEscrowStatus escrowStatus,
    MarketplaceAdminExecutionResult.ExecutionIntent executionIntent,
    MarketplaceAdminExecutionResult.Execution execution,
    MarketplaceAdminExecutionPhase adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    boolean existing) {

  public static ForceMarketplaceAdminExecutionResponseDTO from(
      MarketplaceAdminExecutionResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new ForceMarketplaceAdminExecutionResponseDTO(
        result.reservationId(),
        result.actionType(),
        result.orderKey(),
        result.reservationStatus(),
        result.escrowStatus(),
        result.executionIntent(),
        result.execution(),
        result.adminExecutionPhase(),
        result.nextPollAfterMs(),
        result.pollingEndpoint(),
        result.existing());
  }
}
