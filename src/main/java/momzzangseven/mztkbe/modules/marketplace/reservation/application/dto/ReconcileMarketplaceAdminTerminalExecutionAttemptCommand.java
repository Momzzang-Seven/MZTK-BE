package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for replaying missed terminal hooks for bound marketplace admin attempts. */
public record ReconcileMarketplaceAdminTerminalExecutionAttemptCommand(int batchSize) {

  public void validate() {
    if (batchSize <= 0 || batchSize > 500) {
      throw new Web3InvalidInputException("batchSize must be between 1 and 500");
    }
  }
}
