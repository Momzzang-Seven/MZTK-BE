package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Command for recovering expired unbound marketplace admin execution preparations. */
public record RecoverExpiredMarketplaceAdminExecutionAttemptCommand(
    LocalDateTime now, int batchSize) {

  public void validate() {
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
    if (batchSize <= 0 || batchSize > 500) {
      throw new Web3InvalidInputException("batchSize must be between 1 and 500");
    }
  }
}
