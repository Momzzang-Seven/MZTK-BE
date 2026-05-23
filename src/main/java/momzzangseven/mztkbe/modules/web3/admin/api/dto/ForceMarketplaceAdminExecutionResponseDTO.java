package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminExecutionView;

public record ForceMarketplaceAdminExecutionResponseDTO(
    Long reservationId,
    String actionType,
    String orderKey,
    String reservationStatus,
    String escrowStatus,
    MarketplaceAdminExecutionView.ExecutionIntent executionIntent,
    MarketplaceAdminExecutionView.Execution execution,
    String adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    boolean existing) {

  public static ForceMarketplaceAdminExecutionResponseDTO from(
      MarketplaceAdminExecutionView result) {
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
